package com.modularmc.mmclutils.api.level;

import com.modularmc.mmclutils.api.blockentity.BlockEntityIndex;

import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

/**
 * 由使用方自己登记、注销的按维度索引：每个维度实例各有一份 {@link BlockIndex}，存放在 {@link LevelStore} 里，通常声明成静态常量。
 * 成员由条件决定（成型、入网等）时用它；成员只由方块实体类型决定时改用自动维护的 {@link BlockEntityIndex}。功能：
 * <ul>
 * <li>{@link #create()}：新建一类索引</li>
 * <li>{@link #get}：取该维度的索引，没有就创建；{@link #getIfPresent}：只读，没有时返回 null，不创建</li>
 * <li>{@link #release}：丢弃该维度的索引。维度卸载时通过 {@link LevelStore#releaseAll} 统一释放</li>
 * <li>索引随维度实例一起回收，不经过全局表；单个 {@link BlockIndex} 只在拥有该维度的线程上使用</li>
 * </ul>
 */
public final class LevelBlockIndex<T> {

    private final LevelStore<BlockIndex<T>> store = LevelStore.create(BlockIndex::new);

    private LevelBlockIndex() {}

    /** 为通用方块位置索引分配维度存储槽。每个维度实例持有独立索引。 */
    public static <T> LevelBlockIndex<T> create() {
        return new LevelBlockIndex<>();
    }

    /** 读取指定维度的索引，首次访问时创建。 */
    public BlockIndex<T> get(Level level) {
        return store.get(level);
    }

    /** 查询已经建立的索引，未建立时返回空值。 */
    public @Nullable BlockIndex<T> getIfPresent(Level level) {
        return store.getIfPresent(level);
    }

    /** 丢弃本维度的索引，后续首次读取时可重新创建。 */
    public void release(Level level) {
        store.release(level);
    }
}
