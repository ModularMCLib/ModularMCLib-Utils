package com.modularmc.mmclutils.api.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.TriPredicate;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/** 在六个相邻面上进行有界的广度优先搜索，结果按发现顺序返回。 */
public final class BlockSearch {

    private static final Direction[] SIDES = Direction.values();

    private BlockSearch() {}

    /**
     * 经过未命中的位置，找到匹配位置时停止从该位置继续扩展。
     *
     * @param start       原版打包的起点坐标
     * @param matches     匹配条件，可由调用方决定如何读取位置数据
     * @param resultLimit 匹配位置数的上限
     * @param visitLimit  尝试位置数的上限
     * @return 按发现顺序返回的打包坐标集合
     */
    public static LongSet searchPacked(long start, LongPredicate matches, int resultLimit, int visitLimit) {
        var results = new LongLinkedOpenHashSet();
        var visited = new LongOpenHashSet();
        var pending = new LongArrayFIFOQueue();
        visited.add(start);
        pending.enqueue(start);
        for (int visits = 0; visits < visitLimit && results.size() < resultLimit && !pending.isEmpty(); visits++) {
            long pos = pending.dequeueLong();
            if (matches.test(pos)) {
                results.add(pos);
            } else {
                for (Direction side : SIDES) {
                    long neighbor = BlockPos.offset(pos, side);
                    if (visited.add(neighbor)) pending.enqueue(neighbor);
                }
            }
        }
        return results;
    }

    /** 以方块坐标调用 {@link #searchPacked}，返回独立的方块坐标。 */
    public static Set<BlockPos> search(Predicate<BlockPos> predicate, BlockPos start, int resultLimit, int visitLimit) {
        return unpack(searchPacked(start.asLong(), pos -> predicate.test(BlockPos.of(pos)), resultLimit, visitLimit));
    }

    /** 按原版方向枚举顺序返回与指定位置共面的六个坐标。 */
    public static List<BlockPos> getNeighbors(BlockPos pos) {
        var result = new ObjectArrayList<BlockPos>(SIDES.length);
        for (Direction side : SIDES) result.add(pos.relative(side));
        return result;
    }

    /**
     * 仅从通过筛选的位置继续访问相邻位置。被拒绝的位置仍可经其他路径进入。
     * 每次筛选占一个尝试名额，已经接受的位置跳过。
     *
     * @param start       原版打包的起点坐标
     * @param filter      按到达方向筛选的条件，起点以自身为父位置
     * @param resultLimit 接受位置数的上限
     * @param visitLimit  筛选次数的上限
     * @return 按发现顺序返回的打包坐标集合
     */
    public static LongSet conditionalPacked(long start, EdgeFilter filter, int resultLimit, int visitLimit) {
        var accepted = new LongLinkedOpenHashSet();
        var positions = new LongArrayFIFOQueue();
        var parents = new LongArrayFIFOQueue();
        positions.enqueue(start);
        parents.enqueue(start);
        int visits = 0;
        while (visits < visitLimit && accepted.size() < resultLimit && !positions.isEmpty()) {
            long next = positions.dequeueLong();
            long from = parents.dequeueLong();
            if (accepted.contains(next)) continue;
            visits++;
            if (!filter.accept(from, next, visits == 1)) continue;
            accepted.add(next);
            for (Direction side : SIDES) {
                long neighbor = BlockPos.offset(next, side);
                if (!accepted.contains(neighbor)) {
                    positions.enqueue(neighbor);
                    parents.enqueue(next);
                }
            }
        }
        return accepted;
    }

    /** 使用方块坐标筛选广度优先搜索的每条边。访问起点时父位置为空值。 */
    public static Set<BlockPos> conditionalBlockPosSearch(BlockPos start, BiPredicate<@Nullable BlockPos, BlockPos> filter,
                                                          int resultLimit, int visitLimit) {
        return unpack(conditionalPacked(start.asLong(),
                (parent, pos, first) -> filter.test(first ? null : BlockPos.of(parent), BlockPos.of(pos)),
                resultLimit, visitLimit));
    }

    /**
     * 在已加载区块内查找相邻方块实体。应在世界所属的逻辑线程调用，起点没有所属世界时返回空集合。
     *
     * @param type        允许进入搜索的方块实体类型
     * @param start       搜索起点
     * @param filter      筛选路径，起点的来源实体和方向为空值
     * @param resultLimit 接受实体数的上限
     * @param visitLimit  筛选次数的上限
     * @param <T>         方块实体类型
     * @return 按发现顺序返回的方块实体集合
     */
    public static <T extends BlockEntity> Set<T> conditionalBlockEntitySearch(Class<T> type, T start,
                                                                              TriPredicate<@Nullable T, T, @Nullable Direction> filter,
                                                                              int resultLimit, int visitLimit) {
        Level level = start.getLevel();
        if (level == null) return Set.of();
        var cursor = new BlockPos.MutableBlockPos();
        return conditionalSearch(start, value -> value.getBlockPos().asLong(), pos -> {
            BlockEntity neighbor = LoadedChunks.blockEntity(level, cursor.set(pos));
            return type.isInstance(neighbor) ? type.cast(neighbor) : null;
        }, filter, resultLimit, visitLimit);
    }

    /**
     * 在调用方提供的坐标索引中沿六面搜索。仅从已接受的节点继续扩展。
     * 同一节点可以从多个方向接受筛选，直到其中一次通过。
     *
     * @param start       起点
     * @param position    获取节点的原版打包方块坐标
     * @param lookup      按坐标查询节点，无节点时返回空值
     * @param filter      接收来源节点、目标节点及相对方向，起点的来源和方向为空值
     * @param resultLimit 接受节点数的上限
     * @param visitLimit  筛选次数的上限
     * @param <T>         节点类型
     * @return 按发现顺序返回的节点集合
     */
    public static <T> Set<T> conditionalSearch(T start, ToLongFunction<? super T> position, LongFunction<? extends @Nullable T> lookup,
                                               TriPredicate<@Nullable T, T, @Nullable Direction> filter, int resultLimit, int visitLimit) {
        var accepted = new ObjectLinkedOpenHashSet<T>();
        var pending = new ObjectArrayFIFOQueue<Step<T>>();
        pending.enqueue(new Step<>(null, start, null));
        int visits = 0;
        while (visits < visitLimit && accepted.size() < resultLimit && !pending.isEmpty()) {
            Step<T> step = pending.dequeue();
            if (accepted.contains(step.target)) continue;
            visits++;
            if (!filter.test(step.source, step.target, step.side)) continue;
            accepted.add(step.target);
            long center = position.applyAsLong(step.target);
            for (Direction side : SIDES) {
                T neighbor = lookup.apply(BlockPos.offset(center, side));
                if (neighbor != null && !accepted.contains(neighbor)) {
                    pending.enqueue(new Step<>(step.target, neighbor, side));
                }
            }
        }
        return accepted;
    }

    private static Set<BlockPos> unpack(LongSet positions) {
        var result = new ObjectLinkedOpenHashSet<BlockPos>(positions.size());
        for (var iterator = positions.iterator(); iterator.hasNext();) result.add(BlockPos.of(iterator.nextLong()));
        return result;
    }

    /** 根位置的 root 为真，其他位置提供打包后的父坐标与目标坐标。 */
    @FunctionalInterface
    public interface EdgeFilter {

        /**
         * 判断搜索是否可经过当前边并记录目标位置。
         *
         * @param parent   已访问位置的打包坐标
         * @param position 候选位置的打包坐标
         * @param root     此次调用是否检查起点
         * @return 是否接受候选位置
         */
        boolean accept(long parent, long position, boolean root);
    }

    private record Step<T>(@Nullable T source, T target, @Nullable Direction side) {}
}
