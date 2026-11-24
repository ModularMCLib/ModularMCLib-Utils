package com.modularmc.mmclutils.api.data;

import com.modularmc.mmclutils.ModularMCLibUtils;
import com.modularmc.mmclutils.api.level.LevelStore;
import com.modularmc.mmclutils.api.schedule.SerialTaskExecutor;
import com.modularmc.mmclutils.utils.FileUtils;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** 按维度和稳定标识保存二进制数据。值的修改、取快照及保存调度均在服务器线程执行。 */
@NullMarked
@EventBusSubscriber(modid = ModularMCLibUtils.MOD_ID)
public final class FastSavedData<T> {

    private static final LevelStore<Map<Identifier, FastSavedData<?>>> DATA = LevelStore.create(HashMap::new, entries -> entries.values().forEach(value -> value.save(true)));
    private static final Map<MinecraftServer, SerialTaskExecutor> EXECUTORS = new IdentityHashMap<>();

    private final Type<T> type;
    private final Path path;
    private final T value;
    private final SerialTaskExecutor executor;
    private final ChangeTracker changes = new ChangeTracker();
    private CompletableFuture<Void> pending = CompletableFuture.completedFuture(null);
    private long submitted;

    private FastSavedData(Type<T> type, Path path, T value, SerialTaskExecutor executor) {
        this.type = type;
        this.path = path;
        this.value = value;
        this.executor = executor;
    }

    /** 类型实例和标识由调用方长期持有。同一个标识在一份维度存储中只能注册一种类型。 */
    public static <T> FastSavedData<T> get(ServerLevel level, Type<T> type) throws IOException {
        checkOwner(level);
        var entries = DATA.get(level);
        var existing = entries.get(type.id());
        if (existing != null) {
            if (existing.type != type) throw new IllegalStateException("存档类型重复：" + type.id());
            // 同一个 Type 实例约束了存储项的值类型。
            @SuppressWarnings("unchecked")
            FastSavedData<T> typed = (FastSavedData<T>) existing;
            return typed;
        }
        Path worldRoot = level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path directory = DimensionType.getStorageFolder(level.dimension(), worldRoot).resolve("data/mmclutils").normalize();
        Path path = type.id().withSuffix(".bin").resolveAgainst(directory).normalize();
        if (!path.startsWith(directory)) throw new IllegalArgumentException("存档路径超出维度数据目录：" + type.id());

        boolean imported = false;
        T value;
        if (!Files.notExists(path)) {
            value = FileUtils.read(path, type.reader());
        } else {
            LegacyFile<T> legacy = type.legacy();
            Path oldFile = legacy == null ? null : worldRoot.resolve(legacy.relativePath()).normalize();
            if (oldFile != null && !oldFile.startsWith(worldRoot)) throw new IOException("旧存档路径超出世界目录：" + oldFile);
            imported = oldFile != null && !Files.notExists(oldFile);
            value = imported ? legacy.reader().read(level, oldFile) : type.factory().get();
        }
        var data = new FastSavedData<>(type, path, value, executor(level.getServer()));
        if (imported) data.setDirty();
        entries.put(type.id(), data);
        return data;
    }

    public T value() {
        return value;
    }

    /** 修改 value 后调用。已经在写盘的快照仍可完成，此次修改会进入下一次保存。 */
    public void setDirty() {
        changes.markChanged();
    }

    public boolean isDirty() {
        return changes.isDirty();
    }

    @SubscribeEvent
    private static void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level) saveLevel(level, false);
    }

    @ApiStatus.Internal
    public static void flush(ServerLevel level) {
        checkOwner(level);
        saveLevel(level, true);
    }

    private static void saveLevel(ServerLevel level, boolean wait) {
        var entries = DATA.getIfPresent(level);
        if (entries != null) entries.values().forEach(value -> value.save(wait));
    }

    private void save(boolean wait) {
        long revision = changes.revision();
        if (changes.isDirty() && (submitted != revision || pending.isCompletedExceptionally())) {
            T valueToWrite = type.copyForWrite().apply(value);
            submitted = revision;
            pending = executor.submit(() -> {
                FileUtils.writeAtomically(path, valueToWrite, type.writer());
                changes.persisted(revision);
                return null;
            });
            pending.whenComplete((ignored, failure) -> {
                if (failure != null) ModularMCLibUtils.LOGGER.error("写入二进制存档 {} 失败", path, failure);
            });
        }
        if (wait) pending.handle((ignored, failure) -> null).join();
    }

    private static synchronized SerialTaskExecutor executor(MinecraftServer server) {
        return EXECUTORS.computeIfAbsent(server, ignored -> new SerialTaskExecutor("mmclutils-save"));
    }

    @SubscribeEvent
    private static void onStopped(ServerStoppedEvent event) {
        SerialTaskExecutor executor;
        synchronized (FastSavedData.class) {
            executor = EXECUTORS.remove(event.getServer());
        }
        if (executor != null) executor.close();
    }

    private static void checkOwner(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("存档须在所属服务器线程访问");
    }

    /** copyForWrite 由世界线程调用，返回写盘线程可以独立读取的值。 */
    public record Type<T>(Identifier id, Supplier<T> factory, UnaryOperator<T> copyForWrite,
                          FileUtils.Reader<T> reader, FileUtils.Writer<T> writer,
                          @Nullable LegacyFile<T> legacy) {

        public Type(Identifier id, Supplier<T> factory, UnaryOperator<T> copyForWrite,
                    FileUtils.Reader<T> reader, FileUtils.Writer<T> writer) {
            this(id, factory, copyForWrite, reader, writer, null);
        }
    }

    /** 指定相对于世界目录的旧存档和其读取方式。旧文件读取在服务器线程进行。 */
    public record LegacyFile<T>(Path relativePath, LegacyReader<T> reader) {

        public LegacyFile {
            if (relativePath.isAbsolute()) throw new IllegalArgumentException("旧存档路径须相对于世界目录");
        }
    }

    /** 支持读取原有的二进制或 NBT。需要注册表的读取器可使用所属维度的上下文。 */
    @FunctionalInterface
    public interface LegacyReader<T> {

        T read(ServerLevel level, Path path) throws IOException;
    }
}
