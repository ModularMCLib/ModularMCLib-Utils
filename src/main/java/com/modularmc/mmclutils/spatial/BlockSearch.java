package com.modularmc.mmclutils.spatial;

import com.modularmc.mmclutils.level.LoadedChunks;

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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.LongPredicate;
import java.util.function.Predicate;

/** 在六个相邻面上进行有界的广度优先搜索，结果按发现顺序返回。 */
public final class BlockSearch {

    private static final Direction[] SIDES = Direction.values();

    private BlockSearch() {}

    /** 经过未命中的位置，找到匹配位置时停止从该位置继续扩展。 */
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

    public static Set<BlockPos> search(Predicate<BlockPos> predicate, BlockPos start, int resultLimit, int visitLimit) {
        return unpack(searchPacked(start.asLong(), pos -> predicate.test(BlockPos.of(pos)), resultLimit, visitLimit));
    }

    public static List<BlockPos> getNeighbors(BlockPos pos) {
        var result = new ArrayList<BlockPos>(SIDES.length);
        for (Direction side : SIDES) result.add(pos.relative(side));
        return result;
    }

    /** 每次可按到达的边筛选。被拒绝的位置仍可经其他路径访问。 */
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

    public static Set<BlockPos> conditionalBlockPosSearch(BlockPos start, BiPredicate<@Nullable BlockPos, BlockPos> filter,
                                                          int resultLimit, int visitLimit) {
        return unpack(conditionalPacked(start.asLong(),
                (parent, pos, first) -> filter.test(first ? null : BlockPos.of(parent), BlockPos.of(pos)),
                resultLimit, visitLimit));
    }

    public static <T extends BlockEntity> Set<T> conditionalBlockEntitySearch(Class<T> type, T start,
                                                                              TriPredicate<T, T, Direction> filter,
                                                                              int resultLimit, int visitLimit) {
        Level level = start.getLevel();
        if (level == null) return Set.of();
        var accepted = new LinkedHashSet<T>();
        var pending = new ObjectArrayFIFOQueue<Step<T>>();
        pending.enqueue(new Step<>(null, start, null));
        int visits = 0;
        var cursor = new BlockPos.MutableBlockPos();
        while (visits < visitLimit && accepted.size() < resultLimit && !pending.isEmpty()) {
            Step<T> step = pending.dequeue();
            if (accepted.contains(step.target)) continue;
            visits++;
            if (!filter.test(step.source, step.target, step.side)) continue;
            accepted.add(step.target);
            BlockPos center = step.target.getBlockPos();
            for (Direction side : SIDES) {
                cursor.setWithOffset(center, side);
                BlockEntity neighbor = LoadedChunks.blockEntity(level, cursor);
                if (type.isInstance(neighbor) && !accepted.contains(neighbor)) {
                    pending.enqueue(new Step<>(step.target, type.cast(neighbor), side));
                }
            }
        }
        return accepted;
    }

    private static Set<BlockPos> unpack(LongSet positions) {
        var result = new LinkedHashSet<BlockPos>(positions.size());
        for (var iterator = positions.iterator(); iterator.hasNext();) result.add(BlockPos.of(iterator.nextLong()));
        return result;
    }

    /** 根位置的 root 为真，其他位置提供打包后的父坐标与目标坐标。 */
    @FunctionalInterface
    public interface EdgeFilter {

        boolean accept(long parent, long position, boolean root);
    }

    private record Step<T>(@Nullable T source, T target, @Nullable Direction side) {}
}
