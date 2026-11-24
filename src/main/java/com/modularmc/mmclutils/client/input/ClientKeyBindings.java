package com.modularmc.mmclutils.client.input;

import com.modularmc.mmclutils.ModularMCLibUtils;
import com.modularmc.mmclutils.api.input.SyncedKeyMappings.ChangeSet;
import com.modularmc.mmclutils.api.input.SyncedKeyMappings.Key;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import com.mojang.blaze3d.platform.InputConstants;
import it.unimi.dsi.fastutil.objects.Object2BooleanLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 绑定原版按键，采样组合键并同步状态变化。
 * 登记、查询和回调均在客户端线程执行。绑定须在原版按键注册阶段之前声明。
 */
@EventBusSubscriber(modid = ModularMCLibUtils.MOD_ID, value = Dist.CLIENT)
public final class ClientKeyBindings {

    private static final Reference2ObjectLinkedOpenHashMap<Key, Binding> BINDINGS = new Reference2ObjectLinkedOpenHashMap<>();
    private static @Nullable LocalPlayer owner;

    private ClientKeyBindings() {}

    /** 在客户端模组初始化时绑定并将按键加入原版控制设置。 */
    public static Binding bind(Key key, KeyMapping mapping) {
        return put(key, new Binding(key, () -> mapping, true));
    }

    /** 使用原版已有的按键，供应器在客户端进入世界后首次调用。 */
    public static Binding bindVanilla(Key key, Supplier<KeyMapping> mapping) {
        return put(key, new Binding(key, mapping, false));
    }

    private static Binding put(Key key, Binding binding) {
        if (BINDINGS.putIfAbsent(key, binding) != null) throw new IllegalArgumentException("客户端按键已经绑定 " + key.id());
        return binding;
    }

    /** 将按键设置接入客户端模组总线。 */
    @ApiStatus.Internal
    public static void init(IEventBus modBus) {
        modBus.addListener(ClientKeyBindings::initMappings);
    }

    private static void initMappings(RegisterKeyMappingsEvent event) {
        for (Binding binding : BINDINGS.values()) {
            if (binding.configurable) event.register(binding.mapping());
        }
    }

    @SubscribeEvent
    private static void sampleTick(ClientTickEvent.Pre event) {
        sample(null, false);
        Minecraft client = Minecraft.getInstance();
        for (Binding binding : BINDINGS.values()) {
            for (Consumer<Minecraft> listener : binding.tickListeners) listener.accept(client);
        }
    }

    @SubscribeEvent
    private static void sampleKeyboard(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_REPEAT) {
            sample(InputConstants.getKey(event.getKeyEvent()), event.getAction() == GLFW.GLFW_PRESS);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    private static void sampleMouse(InputEvent.MouseButton.Pre event) {
        var key = InputConstants.Type.MOUSE.getOrCreate(event.getButton());
        boolean pressed = event.getAction() == GLFW.GLFW_PRESS;
        sample(key, pressed);
        if (!pressed) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.screen != null || !client.isWindowActive() || client.isPaused()) return;
        int modifiers = ClientInput.modifiers();
        for (Binding binding : BINDINGS.values()) {
            var capture = binding.captureInput;
            if (capture == null) continue;
            KeyMapping mapping = binding.mapping();
            if (mapping.getKey().equals(key) && binding.matches(mapping, modifiers) && capture.test(client)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    private static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        Minecraft client = Minecraft.getInstance();
        double x = client.mouseHandler.xpos() * event.getScreen().width / client.getWindow().getScreenWidth();
        double y = client.mouseHandler.ypos() * event.getScreen().height / client.getWindow().getScreenHeight();
        if (dispatchScreen(event.getScreen(), InputConstants.getKey(event.getKeyEvent()), x, y)) event.setCanceled(true);
    }

    @SubscribeEvent
    private static void onScreenMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (dispatchScreen(event.getScreen(), InputConstants.Type.MOUSE.getOrCreate(event.getButton()),
                event.getMouseX(), event.getMouseY()))
            event.setCanceled(true);
    }

    private static boolean dispatchScreen(Screen screen, InputConstants.Key key, double x, double y) {
        if (screen instanceof KeyBindsScreen) return false;
        int modifiers = ClientInput.modifiers();
        for (Binding binding : BINDINGS.values()) {
            if (binding.screenListeners.isEmpty()) continue;
            KeyMapping mapping = binding.mapping();
            if (mapping.isUnbound() || !mapping.getKey().equals(key) || !binding.matches(mapping, modifiers)) continue;
            binding.waitingForRelease = true;
            for (ScreenListener listener : binding.screenListeners) {
                if (listener.pressed(screen, binding, x, y)) return true;
            }
        }
        return false;
    }

    private static void sample(InputConstants.@Nullable Key changed, boolean down) {
        if (BINDINGS.isEmpty()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (owner != client.player) {
            clearLocalState();
            owner = client.player;
        }
        boolean available = client.isWindowActive() && client.screen == null && !client.isPaused();
        int modifiers = available ? ClientInput.modifiers() : 0;
        ObjectArrayList<Binding> changes = null;
        for (Binding binding : BINDINGS.values()) {
            KeyMapping mapping = binding.mapping();
            boolean mainDown = ClientInput.mainKeyDown(client.getWindow(), mapping, changed, down);
            if (!mainDown) binding.waitingForRelease = false;
            else if (!available) binding.waitingForRelease = true;
            boolean pressed = mainDown && available && !binding.waitingForRelease && binding.matches(mapping, modifiers);
            if (binding.down == pressed) continue;
            binding.down = pressed;
            if (changes == null) changes = new ObjectArrayList<>();
            changes.add(binding);
        }
        if (changes == null) return;
        var connection = client.getConnection();
        if (connection != null && connection.hasChannel(ChangeSet.TYPE)) send(changes, connection::send);
        for (Binding binding : changes) binding.notifyListeners();
    }

    private static void send(ObjectArrayList<Binding> changes, Consumer<ChangeSet> send) {
        var payload = new Object2BooleanLinkedOpenHashMap<Identifier>(Math.min(changes.size(), ChangeSet.MAX_CHANGES));
        for (Binding binding : changes) {
            payload.put(binding.key.id(), binding.down);
            if (payload.size() == ChangeSet.MAX_CHANGES) {
                send.accept(new ChangeSet(payload));
                payload = new Object2BooleanLinkedOpenHashMap<>();
            }
        }
        if (!payload.isEmpty()) send.accept(new ChangeSet(payload));
    }

    @SubscribeEvent
    private static void clearOnDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        clearLocalState();
        owner = null;
    }

    private static void clearLocalState() {
        for (Binding binding : BINDINGS.values()) {
            binding.waitingForRelease = false;
            if (!binding.down) continue;
            binding.down = false;
            binding.notifyListeners();
        }
    }

    /** 客户端按键绑定。状态和修饰键仅在客户端线程修改。 */
    public static final class Binding {

        private final Key key;
        private final Supplier<KeyMapping> factory;
        private final boolean configurable;
        private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<Consumer<Minecraft>> tickListeners = new CopyOnWriteArrayList<>();
        private final CopyOnWriteArrayList<ScreenListener> screenListeners = new CopyOnWriteArrayList<>();
        private @Nullable KeyMapping mapping;
        private @Nullable Predicate<Minecraft> captureInput;
        private int extraModifiers;
        private boolean down;
        private boolean waitingForRelease;

        private Binding(Key key, Supplier<KeyMapping> factory, boolean configurable) {
            this.key = key;
            this.factory = factory;
            this.configurable = configurable;
        }

        /** 返回两端共同声明的按键。 */
        public Key key() {
            return key;
        }

        /** 返回最近一次采样的游戏内状态。屏幕打开、失焦或断开后进入释放状态。 */
        public boolean isDown() {
            return down;
        }

        /** 增加组合键条件。控制设置中的主键和原版修饰键仍由原版存储。 */
        public Binding withModifiers(int modifiers) {
            if ((modifiers & ~ClientInput.ALL_MODIFIERS) != 0) {
                throw new IllegalArgumentException("不支持的修饰键组合 " + modifiers);
            }
            extraModifiers = modifiers;
            return this;
        }

        /** 添加本地状态监听，相同监听只登记一次。 */
        public void addListener(Listener listener) {
            listeners.addIfAbsent(listener);
        }

        /** 移除本地状态监听。 */
        public void removeListener(Listener listener) {
            listeners.remove(listener);
        }

        /**
         * 登记每个客户端 Tick 执行的回调。它在更新同步按键状态之后运行，主菜单也会调用。
         *
         * @param listener 接收当前客户端实例的回调，相同回调只登记一次
         */
        public void addTickListener(Consumer<Minecraft> listener) {
            tickListeners.addIfAbsent(listener);
        }

        /** 移除客户端 Tick 回调。 */
        public void removeTickListener(Consumer<Minecraft> listener) {
            tickListeners.remove(listener);
        }

        /**
         * 设置何时消费当前绑定对应的游戏内鼠标按下事件。
         * 同步状态仍会更新，释放事件照常交给原版以清理鼠标状态。
         *
         * @param condition 客户端线程上运行的消费条件，空值清除条件
         * @return 当前绑定
         */
        public Binding captureInputWhen(@Nullable Predicate<Minecraft> condition) {
            captureInput = condition;
            return this;
        }

        /** 登记屏幕按下回调，用于界面内与游戏状态独立的按键操作。 */
        public void addScreenListener(ScreenListener listener) {
            screenListeners.addIfAbsent(listener);
        }

        /** 移除屏幕按下回调。 */
        public void removeScreenListener(ScreenListener listener) {
            screenListeners.remove(listener);
        }

        /** 返回当前绑定使用的原版按键，首次访问时向绑定提供者取得对象。 */
        public KeyMapping mapping() {
            if (mapping == null) mapping = factory.get();
            return mapping;
        }

        private boolean matches(KeyMapping mapping, int modifiers) {
            if (extraModifiers == 0) return mapping.isConflictContextAndModifierActive();
            int mainModifier = ClientInput.keyModifierBit(mapping.getKey());
            int mappingModifiers = mapping instanceof KeyChordMapping chord ? chord.modifiers() : ClientInput.modifierBit(mapping.getKeyModifier());
            int expected = (extraModifiers | mappingModifiers) & ~mainModifier;
            return mapping.getKeyConflictContext().isActive() && (modifiers & ~mainModifier) == expected;
        }

        private void notifyListeners() {
            for (Listener listener : listeners) listener.changed(this, down);
        }
    }

    /** 在客户端线程接收绑定的按下和释放通知。 */
    @FunctionalInterface
    public interface Listener {

        /**
         * 在本地绑定状态已经更新时调用。失焦与断开连接都会产生释放。
         *
         * @param binding 发出状态变化的客户端绑定
         * @param down    新状态，真为按下
         */
        void changed(Binding binding, boolean down);
    }

    /** 在客户端线程处理屏幕按键。只向当前屏幕派发，且不会发送游戏内按键状态。 */
    @FunctionalInterface
    public interface ScreenListener {

        /**
         * 在当前屏幕接收一个已匹配的按下边沿。
         *
         * @param screen  当前屏幕
         * @param binding 匹配的客户端绑定
         * @param mouseX  鼠标在屏幕中的横向坐标
         * @param mouseY  鼠标在屏幕中的纵向坐标
         * @return 是否消费本次输入
         */
        boolean pressed(Screen screen, Binding binding, double mouseX, double mouseY);
    }
}
