package com.modularmc.mmclutils.api.level;

import com.modularmc.mmclutils.ModularMCLibUtils;

import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 按维度实例存放一份对象：对象直接挂在 {@link Level} 实例上的槽位数组里，随维度实例一起回收，不经过任何全局表。功能：
 * <ul>
 * <li>{@link #create}/{@link #createWithLevel}：新建一种存放，分配一个全局唯一的槽位号，可带释放回调；通常声明成静态常量，任何时候都能创建</li>
 * <li>{@link #get}：取该维度的对象，没有就用工厂创建，多个线程同时首次访问也只创建一份；{@link #getIfPresent}：只读，没有时返回 null，不创建</li>
 * <li>{@link #set}：直接放入或清空（传 null）该维度的对象，不调用释放回调</li>
 * <li>{@link #release}：丢弃该维度的这一份并调用释放回调；{@link #releaseAll}：维度卸载时逐个调用释放回调后丢弃全部，之后 {@link #isReleased}
 * 为真</li>
 * <li>按维度实例区分，客户端和服务端的维度互不影响；任何线程都能读写，存放的对象本身是否线程安全由使用方负责</li>
 * </ul>
 */
@EventBusSubscriber(modid = ModularMCLibUtils.MOD_ID)
public final class LevelStore<T> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final VarHandle SLOT = MethodHandles.arrayElementVarHandle(Object[].class);
    private static final AtomicInteger SLOTS = new AtomicInteger();
    private static volatile LevelStore<?>[] stores = new LevelStore<?>[0];

    private final int slot;
    private final Function<Object, ? extends T> factory;
    private final @Nullable Consumer<? super T> onRelease;

    private LevelStore(Function<Object, ? extends T> factory, @Nullable Consumer<? super T> onRelease) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.onRelease = onRelease;
        synchronized (LevelStore.class) {
            this.slot = SLOTS.getAndIncrement();
            LevelStore<?>[] grown = Arrays.copyOf(stores, slot + 1);
            grown[slot] = this;
            stores = grown;
        }
    }

    /** 分配一个可复用的维度槽。工厂只在首次读取空槽时调用，须返回非空值。 */
    public static <T> LevelStore<T> create(Supplier<? extends T> factory) {
        Objects.requireNonNull(factory, "factory");
        return new LevelStore<>(holder -> factory.get(), null);
    }

    /** 分配带释放回调的槽。维度卸载或显式释放此槽时，回调接收原值。 */
    public static <T> LevelStore<T> create(Supplier<? extends T> factory, Consumer<? super T> onRelease) {
        Objects.requireNonNull(factory, "factory");
        return new LevelStore<>(holder -> factory.get(), Objects.requireNonNull(onRelease, "onRelease"));
    }

    /** 分配维度槽，并把发生首次读取的世界实例交给工厂。 */
    public static <T> LevelStore<T> createWithLevel(Function<? super Level, ? extends T> factory) {
        Objects.requireNonNull(factory, "factory");
        return new LevelStore<>(holder -> factory.apply((Level) holder), null);
    }

    /** 分配维度槽，工厂接收世界实例，释放回调接收被移除的值。 */
    public static <T> LevelStore<T> createWithLevel(Function<? super Level, ? extends T> factory, Consumer<? super T> onRelease) {
        Objects.requireNonNull(factory, "factory");
        return new LevelStore<>(holder -> factory.apply((Level) holder), Objects.requireNonNull(onRelease, "onRelease"));
    }

    /** 取得本维度的值，首次访问空槽时同步创建。 */
    public T get(Level level) {
        return forHolder((SlotHolder) level);
    }

    /** 只查询现有值，空槽时返回空值。 */
    public @Nullable T getIfPresent(Level level) {
        return forHolderIfPresent((SlotHolder) level);
    }

    /** 替换值，传入空值可清空槽。此操作不会调用释放回调。 */
    public void set(Level level, @Nullable T value) {
        setHolder((SlotHolder) level, value);
    }

    /** 清空指定维度的本槽，并在调用线程释放原值。 */
    public void release(Level level) {
        releaseHolder((SlotHolder) level);
    }

    /** 释放维度的全部现有槽，并标记该实例已经卸载。 */
    public static void releaseAll(Level level) {
        releaseAllHolder((SlotHolder) level);
    }

    @SubscribeEvent
    private static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) releaseAll(level);
    }

    /** 查询世界是否已收到存储释放标记。 */
    public static boolean isReleased(Level level) {
        return ((SlotHolder) level).mmclutils$released();
    }

    /** 供注入代码使用，按槽读取并初始化持有者的值。 */
    @ApiStatus.Internal
    @SuppressWarnings("unchecked")
    public T forHolder(SlotHolder holder) {
        Object[] slots = holder.mmclutils$slots();
        if (slots != null && slot < slots.length) {
            Object value = SLOT.getAcquire(slots, slot);
            if (value != null) return (T) value;
        }
        return createIn(holder);
    }

    /** 供注入代码读取已发布的槽内容。槽未分配或无值时返回空值。 */
    @ApiStatus.Internal
    @SuppressWarnings("unchecked")
    public @Nullable T forHolderIfPresent(SlotHolder holder) {
        Object[] slots = holder.mmclutils$slots();
        return slots != null && slot < slots.length ? (T) SLOT.getAcquire(slots, slot) : null;
    }

    /** 供注入代码替换或清空槽内容。替换时由调用处决定旧值的释放。 */
    @ApiStatus.Internal
    public void setHolder(SlotHolder holder, @Nullable T value) {
        synchronized (holder) {
            if (value == null) {
                Object[] slots = holder.mmclutils$slots();
                if (slots != null && slot < slots.length) SLOT.setRelease(slots, slot, (Object) null);
                return;
            }
            SLOT.setRelease(capacity(holder), slot, (Object) value);
        }
    }

    /** 从持有者取走现有值，离开持有者的锁后执行释放回调。 */
    @ApiStatus.Internal
    @SuppressWarnings("unchecked")
    public void releaseHolder(SlotHolder holder) {
        Object value;
        synchronized (holder) {
            Object[] slots = holder.mmclutils$slots();
            if (slots == null || slot >= slots.length) return;
            value = SLOT.getAcquire(slots, slot);
            SLOT.setRelease(slots, slot, (Object) null);
        }
        if (value != null) notifyRelease((T) value);
    }

    /** 摘除持有者的槽数组，再逐槽执行各自的释放回调。 */
    @ApiStatus.Internal
    public static void releaseAllHolder(SlotHolder holder) {
        Object[] slots;
        synchronized (holder) {
            slots = holder.mmclutils$slots();
            holder.mmclutils$setSlots(null);
            holder.mmclutils$setReleased(true);
        }
        if (slots == null) return;
        LevelStore<?>[] all = stores;
        for (int i = 0; i < slots.length && i < all.length; i++) {
            Object value = SLOT.getAcquire(slots, i);
            if (value != null) all[i].notifyReleaseUnchecked(value);
        }
    }

    @SuppressWarnings("unchecked")
    private T createIn(SlotHolder holder) {
        synchronized (holder) {
            Object[] slots = capacity(holder);
            Object value = SLOT.getAcquire(slots, slot);
            if (value != null) return (T) value;
            T created = Objects.requireNonNull(factory.apply(holder), "factory returned null");
            SLOT.setRelease(capacity(holder), slot, (Object) created);
            return created;
        }
    }

    private Object[] capacity(SlotHolder holder) {
        Object[] slots = holder.mmclutils$slots();
        if (slots != null && slot < slots.length) return slots;
        Object[] grown = new Object[Math.max(slot + 1, SLOTS.get())];
        if (slots != null) System.arraycopy(slots, 0, grown, 0, slots.length);
        holder.mmclutils$setSlots(grown);
        return grown;
    }

    @SuppressWarnings("unchecked")
    private void notifyReleaseUnchecked(Object value) {
        notifyRelease((T) value);
    }

    private void notifyRelease(T value) {
        if (onRelease == null) return;
        try {
            onRelease.accept(value);
        } catch (Throwable t) {
            LOGGER.error("维度存储的释放回调失败", t);
        }
    }

    /** 由维度实例持有的存储槽及卸载状态。写入由 LevelStore 负责。 */
    @ApiStatus.Internal
    public interface SlotHolder {

        /** 初次分配前返回空值。 */
        @Nullable
        Object[] mmclutils$slots();

        /** 分配、扩容或释放时发布槽数组。 */
        void mmclutils$setSlots(@Nullable Object[] slots);

        /** 查询该维度实例是否已经释放。 */
        boolean mmclutils$released();

        /** 在卸载时发布该维度实例的释放状态。 */
        void mmclutils$setReleased(boolean released);
    }
}
