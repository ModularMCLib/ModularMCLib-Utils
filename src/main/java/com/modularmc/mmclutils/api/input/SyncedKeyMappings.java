package com.modularmc.mmclutils.api.input;

import com.modularmc.mmclutils.ModularMCLibUtils;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import io.netty.handler.codec.DecoderException;
import it.unimi.dsi.fastutil.objects.Object2BooleanLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.ApiStatus;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** 使用共同的标识声明同步按键，按服务端玩家维护按下状态。 */
@EventBusSubscriber(modid = ModularMCLibUtils.MOD_ID)
public final class SyncedKeyMappings {

    private static final ConcurrentHashMap<Identifier, Key> KEYS = new ConcurrentHashMap<>();

    private SyncedKeyMappings() {}

    /** 在模组初始化时于两端声明一次。标识应使用调用方的命名空间，重复声明会抛出异常。 */
    public static Key define(Identifier id) {
        var key = new Key(id);
        if (KEYS.putIfAbsent(id, key) != null) throw new IllegalArgumentException("同步按键已经声明 " + id);
        return key;
    }

    @ApiStatus.Internal
    public static void init(IEventBus modBus) {
        modBus.addListener(SyncedKeyMappings::initPayload);
    }

    private static void initPayload(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToServer(ChangeSet.TYPE, ChangeSet.CODEC,
                (payload, context) -> apply((ServerPlayer) context.player(), payload.changes));
    }

    private static void apply(ServerPlayer player, Object2BooleanMap<Identifier> changes) {
        var accepted = new Object2BooleanLinkedOpenHashMap<Key>();
        for (var change : changes.object2BooleanEntrySet()) {
            Key key = KEYS.get(change.getKey());
            if (key == null) continue;
            boolean pressed = change.getBooleanValue();
            if (key.setPressed(player, pressed)) accepted.put(key, pressed);
        }
        for (var change : accepted.object2BooleanEntrySet()) {
            change.getKey().notifyListeners(player, change.getBooleanValue());
        }
    }

    @SubscribeEvent
    private static void clearOnLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        release((ServerPlayer) event.getEntity());
    }

    @SubscribeEvent
    private static void clearReplacedPlayer(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer player) release(player);
    }

    @SubscribeEvent
    private static void clearStoppedServer(ServerStoppedEvent event) {
        MinecraftServer server = event.getServer();
        for (Key key : KEYS.values()) {
            key.pressed.removeIf(player -> player.level().getServer() == server);
            key.playerListeners.keySet().removeIf(player -> player.level().getServer() == server);
        }
    }

    private static void release(ServerPlayer player) {
        var released = new ReferenceOpenHashSet<Key>();
        for (Key key : KEYS.values()) {
            if (key.setPressed(player, false)) released.add(key);
        }
        try {
            for (Key key : released) key.notifyListeners(player, false);
        } finally {
            for (Key key : KEYS.values()) key.playerListeners.remove(player);
        }
    }

    /** 一个可被客户端绑定并由服务端读取的按键。状态和玩家监听仅在服务端线程访问。 */
    public static final class Key {

        private final Identifier id;
        private final ReferenceOpenHashSet<ServerPlayer> pressed = new ReferenceOpenHashSet<>();
        private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
        private final Reference2ObjectOpenHashMap<ServerPlayer, CopyOnWriteArrayList<Listener>> playerListeners = new Reference2ObjectOpenHashMap<>();

        private Key(Identifier id) {
            this.id = id;
        }

        public Identifier id() {
            return id;
        }

        /** 返回玩家最近同步的状态，可用于界面或操作选择。游戏条件仍由服务端判断。 */
        public boolean isDown(ServerPlayer player) {
            return pressed.contains(player);
        }

        public void addListener(Listener listener) {
            listeners.addIfAbsent(listener);
        }

        public void removeListener(Listener listener) {
            listeners.remove(listener);
        }

        /** 添加只接收此玩家状态变化的监听。玩家退出或被替换时清理。 */
        public void addListener(ServerPlayer player, Listener listener) {
            playerListeners.computeIfAbsent(player, ignored -> new CopyOnWriteArrayList<>()).addIfAbsent(listener);
        }

        public void removeListener(ServerPlayer player, Listener listener) {
            var current = playerListeners.get(player);
            if (current != null && current.remove(listener) && current.isEmpty()) playerListeners.remove(player);
        }

        private boolean setPressed(ServerPlayer player, boolean down) {
            return down ? pressed.add(player) : pressed.remove(player);
        }

        private void notifyListeners(ServerPlayer player, boolean down) {
            for (Listener listener : listeners) listener.changed(player, this, down);
            var current = playerListeners.get(player);
            if (current != null) {
                for (Listener listener : current) listener.changed(player, this, down);
            }
        }
    }

    /** 在服务端线程接收状态边沿，可在回调中查询同一批更新的其他按键。 */
    @FunctionalInterface
    public interface Listener {

        /** 在状态已更新时收到通知。退出时仍处于按下状态的键会发出释放通知。 */
        void changed(ServerPlayer player, Key key, boolean down);
    }

    @ApiStatus.Internal
    public record ChangeSet(Object2BooleanMap<Identifier> changes) implements CustomPacketPayload {

        public static final int MAX_CHANGES = 64;
        public static final Type<ChangeSet> TYPE = new Type<>(ModularMCLibUtils.id("key_state"));
        private static final StreamCodec<FriendlyByteBuf, ChangeSet> CODEC = StreamCodec.of(ChangeSet::write, ChangeSet::read);

        private static ChangeSet read(FriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 1 || count > MAX_CHANGES) throw new DecoderException("同步按键数量超出范围 " + count);
            var changes = new Object2BooleanLinkedOpenHashMap<Identifier>(count);
            for (int i = 0; i < count; i++) {
                Identifier id = Identifier.STREAM_CODEC.decode(buffer);
                if (changes.containsKey(id)) throw new DecoderException("同步按键标识重复 " + id);
                changes.put(id, buffer.readBoolean());
            }
            return new ChangeSet(changes);
        }

        private static void write(FriendlyByteBuf buffer, ChangeSet state) {
            buffer.writeVarInt(state.changes.size());
            for (var change : state.changes.object2BooleanEntrySet()) {
                Identifier.STREAM_CODEC.encode(buffer, change.getKey());
                buffer.writeBoolean(change.getBooleanValue());
            }
        }

        @Override
        public Type<ChangeSet> type() {
            return TYPE;
        }
    }
}
