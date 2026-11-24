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

/**
 * 按维度和稳定标识保存紧凑二进制数据。在所属服务器线程修改值，并在修改后调用 {@link #setDirty()}。
 * 保存时复制本次要写的数据。服务器的串行写入队列保序写盘，成功后仅确认本次提交的数据版本。
 * 常规世界保存异步排队，强制刷盘与世界卸载等待已提交的操作完成。
 *
 * @param <T> 存档值的类型
 */
@NullMarked
@EventBusSubscriber(modid = ModularMCLibUtils.MOD_ID)
public final class FastSavedData<T> {

    private static final LevelStore<Map<Identifier, FastSavedData<?>>> DATA = LevelStore.create(HashMap::new, entries -> entries.values().forEach(value -> value.save(true)));
    private static final Map<MinecraftServer, SerialTaskExecutor> EXECUTORS = new IdentityHashMap<>();

    private final Type<T> type;
    private final Path path;
    private final T value;
    private final SerialTaskExecutor executor;
    private volatile long revision;
    private volatile long savedRevision;
    private CompletableFuture<Void> pending = CompletableFuture.completedFuture(null);
    private long submitted;

    private FastSavedData(Type<T> type, Path path, T value, SerialTaskExecutor executor) {
        this.type = type;
        this.path = path;
        this.value = value;
        this.executor = executor;
    }

    /**
     * 在服务器线程取得或读取该维度的存档。新文件缺失时尝试调用方指定的旧文件。
     * 只有两个文件都缺失时才调用默认值工厂，成功读取旧数据后会安排保存为新文件。
     *
     * @param level 所属维度
     * @param type  由调用方长期持有的类型。同一维度中每个标识只能对应一种类型
     * @param <T>   存档值的类型
     * @return 维度生命周期内复用的存档对象
     * @throws IOException           文件读取失败或数据格式无效
     * @throws IllegalStateException 从非所属服务器线程访问，或同标识已用于另一类型
     */
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

    /** 取得服务器线程持有的存档值。修改其内容后须调用 {@link #setDirty()}。 */
    public T value() {
        return value;
    }

    /** 在所属服务器线程修改值后调用。已经提交的写盘操作可以完成，最新数据将在下一次保存中写入。 */
    public void setDirty() {
        revision++;
    }

    /** 查询是否还存在尚未成功写入的数据版本。 */
    public boolean isDirty() {
        return revision != savedRevision;
    }

    @SubscribeEvent
    private static void onLevelSave(LevelEvent.Save event) {
        if (event.getLevel() instanceof ServerLevel level) saveLevel(level, false);
    }

    /** 在所属服务器线程提交全部待保存数据并等待写盘队列完成。 */
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
        long writingRevision = revision;
        if (writingRevision != savedRevision && (submitted != writingRevision || pending.isCompletedExceptionally())) {
            T valueToWrite = type.copyForWrite().apply(value);
            submitted = writingRevision;
            pending = executor.submit(() -> {
                FileUtils.writeAtomically(path, valueToWrite, type.writer());
                // 写盘期间发生的修改留给下一次保存。
                savedRevision = writingRevision;
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

    /**
     * 定义一个存档的标识、复制方式和读写格式。
     *
     * @param id           用于文件位置的稳定标识
     * @param factory      新旧文件均不存在时提供默认值
     * @param copyForWrite 在世界线程复制写入所需数据。返回值与继续在世界线程修改的状态独立
     * @param reader       在服务器线程解码当前格式
     * @param writer       在服务器的后台队列编码复制后的数据
     * @param legacy       仅在新文件缺失时使用的旧格式入口，可以为空
     * @param <T>          存档值的类型
     */
    public record Type<T>(Identifier id, Supplier<T> factory, UnaryOperator<T> copyForWrite,
                          FileUtils.Reader<T> reader, FileUtils.Writer<T> writer,
                          @Nullable LegacyFile<T> legacy) {

        /**
         * 定义只读取当前文件格式的存档类型。
         *
         * @param id           用于文件位置的稳定标识
         * @param factory      文件不存在时提供默认值
         * @param copyForWrite 在世界线程复制写入所需数据
         * @param reader       解码当前文件
         * @param writer       在后台编码交付的数据
         */
        public Type(Identifier id, Supplier<T> factory, UnaryOperator<T> copyForWrite,
                    FileUtils.Reader<T> reader, FileUtils.Writer<T> writer) {
            this(id, factory, copyForWrite, reader, writer, null);
        }
    }

    /**
     * 指定可从旧布局导入的文件。在服务器线程使用，并可访问所属维度的注册表。
     *
     * @param relativePath 相对整个世界根目录的路径
     * @param reader       文件存在时使用的旧格式读取器
     * @param <T>          读取值的类型
     */
    public record LegacyFile<T>(Path relativePath, LegacyReader<T> reader) {

        /** 指定世界目录下的旧文件和读取器。路径须为相对路径。 */
        public LegacyFile {
            if (relativePath.isAbsolute()) throw new IllegalArgumentException("旧存档路径须相对于世界目录");
        }
    }

    /**
     * 在服务器线程读取旧格式，仅在当前存档缺失时调用。
     *
     * @param <T> 读取得到的存档值的类型
     */
    @FunctionalInterface
    public interface LegacyReader<T> {

        /**
         * 读取旧二进制或 NBT 文件，并关闭自行打开的流。
         *
         * @param level 所属维度，可用于查询注册表
         * @param path  已确认存在的旧文件
         * @return 可由当前存档类型保存的非空值
         * @throws IOException 打开或读取文件失败，或者文件格式无效
         */
        T read(ServerLevel level, Path path) throws IOException;
    }
}
