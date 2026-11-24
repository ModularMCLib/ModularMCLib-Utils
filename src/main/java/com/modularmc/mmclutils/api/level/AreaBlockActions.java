package com.modularmc.mmclutils.api.level;

import com.modularmc.mmclutils.api.schedule.Cadence;
import com.modularmc.mmclutils.api.schedule.Schedulers;
import com.modularmc.mmclutils.api.schedule.Task;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import it.unimi.dsi.fastutil.longs.LongIterator;

import java.util.function.LongPredicate;

/** 在服务器线程操作已加载区域。通过回调决定方块操作及其原版更新规则。 */
public final class AreaBlockActions {

    private AreaBlockActions() {}

    /** 在服务端线程访问两个方块位置构成的闭合立方体。返回回调报告成功的次数。 */
    public static long box(ServerLevel level, BlockPos first, BlockPos second, Action action) {
        return visit(level, Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()),
                Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()), pos -> true, action);
    }

    /** 两角按区块坐标传入，覆盖其间所有区块的完整高度。 */
    public static long chunks(ServerLevel level, int firstX, int firstZ, int secondX, int secondZ, Action action) {
        return visit(level, Math.multiplyExact(Math.min(firstX, secondX), 16), level.getMinBuildHeight(), Math.multiplyExact(Math.min(firstZ, secondZ), 16),
                Math.multiplyExact(Math.max(firstX, secondX), 16) + 15, level.getMaxBuildHeight() - 1, Math.multiplyExact(Math.max(firstZ, secondZ), 16) + 15, pos -> true, action);
    }

    /** 按水平方向的圆形距离筛选指定高度区间的方块。负半径将被拒绝。 */
    public static long cylinder(ServerLevel level, BlockPos center, int radius, int minY, int maxY, Action action) {
        if (radius < 0) throw new IllegalArgumentException("圆柱半径不能为负数");
        long radiusSquared = (long) radius * radius;
        return visit(level, Math.subtractExact(center.getX(), radius), minY, Math.subtractExact(center.getZ(), radius),
                Math.addExact(center.getX(), radius), maxY, Math.addExact(center.getZ(), radius), pos -> {
                    long dx = (long) BlockPos.getX(pos) - center.getX();
                    long dz = (long) BlockPos.getZ(pos) - center.getZ();
                    return dz * dz <= radiusSquared - dx * dx;
                }, action);
    }

    /** 访问到中心的欧氏距离不超过给定半径的格点。仅处理仍处于加载状态的方块。 */
    public static long sphere(ServerLevel level, BlockPos center, int radius, Action action) {
        if (radius < 0) throw new IllegalArgumentException("球形半径不能为负数");
        long radiusSquared = (long) radius * radius;
        return visit(level, Math.subtractExact(center.getX(), radius), Math.subtractExact(center.getY(), radius), Math.subtractExact(center.getZ(), radius),
                Math.addExact(center.getX(), radius), Math.addExact(center.getY(), radius), Math.addExact(center.getZ(), radius), pos -> {
                    long dx = (long) BlockPos.getX(pos) - center.getX();
                    long dy = (long) BlockPos.getY(pos) - center.getY();
                    long dz = (long) BlockPos.getZ(pos) - center.getZ();
                    return dz * dz <= radiusSquared - dx * dx - dy * dy;
                }, action);
    }

    /** 先完成连通搜索，再按发现顺序修改，避免操作改变本次搜索结果。 */
    public static long connected(ServerLevel level, BlockPos start, BlockSearch.EdgeFilter filter,
                                 int resultLimit, int visitLimit, Action action) {
        checkThread(level);
        var positions = BlockSearch.conditionalPacked(start.asLong(),
                (parent, pos, root) -> !level.isOutsideBuildHeight(BlockPos.getY(pos)) && LoadedChunks.chunk(level, BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4) != null && filter.accept(parent, pos, root), resultLimit, visitLimit);
        return apply(level, positions.iterator(), action);
    }

    /** 从当前位置消耗打包方块坐标迭代器，跳过未加载或超出高度范围的位置。返回成功操作数。 */
    public static long apply(ServerLevel level, LongIterator positions, Action action) {
        checkThread(level);
        long count = 0;
        var cursor = new BlockPos.MutableBlockPos();
        while (positions.hasNext()) {
            if (applyAt(level, positions.nextLong(), cursor, action)) count++;
        }
        return count;
    }

    /** 每轮最多消耗 limit 个位置。序列归任务独占，维度关闭时调度器回收任务。 */
    public static Task schedule(ServerLevel level, LongIterator positions, int limit, Action action) {
        checkThread(level);
        if (limit < 1) throw new IllegalArgumentException("每 Tick 的位置上限必须为正数");
        var cursor = new BlockPos.MutableBlockPos();
        return Schedulers.of(level).repeat(Cadence.every(1), positions, iterator -> {
            for (int index = 0; index < limit && iterator.hasNext(); index++) {
                applyAt(level, iterator.nextLong(), cursor, action);
            }
        }, iterator -> !iterator.hasNext());
    }

    private static long visit(ServerLevel level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                              LongPredicate contains, Action action) {
        checkThread(level);
        minY = Math.max(minY, level.getMinBuildHeight());
        maxY = Math.min(maxY, level.getMaxBuildHeight() - 1);
        if (minY > maxY) return 0;
        if (BlockPos.getX(BlockPos.asLong(minX, 0, 0)) != minX || BlockPos.getX(BlockPos.asLong(maxX, 0, 0)) != maxX || BlockPos.getZ(BlockPos.asLong(0, 0, minZ)) != minZ || BlockPos.getZ(BlockPos.asLong(0, 0, maxZ)) != maxZ) {
            throw new IllegalArgumentException("区域超出可编码的方块坐标");
        }
        long count = 0;
        var cursor = new BlockPos.MutableBlockPos();
        for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
            for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
                LevelChunk chunk = LoadedChunks.chunk(level, chunkX, chunkZ);
                if (chunk == null) continue;
                int x0 = Math.max(minX, chunkX << 4), x1 = Math.min(maxX, (chunkX << 4) + 15);
                int z0 = Math.max(minZ, chunkZ << 4), z1 = Math.min(maxZ, (chunkZ << 4) + 15);
                for (int y = minY; y <= maxY; y++) {
                    for (int z = z0; z <= z1; z++) {
                        for (int x = x0; x <= x1; x++) {
                            if (contains.test(BlockPos.asLong(x, y, z)) && action.apply(level, cursor.set(x, y, z), chunk.getBlockState(cursor))) count++;
                        }
                    }
                }
            }
        }
        return count;
    }

    private static boolean applyAt(ServerLevel level, long position, BlockPos.MutableBlockPos cursor, Action action) {
        cursor.set(BlockPos.getX(position), BlockPos.getY(position), BlockPos.getZ(position));
        if (level.isOutsideBuildHeight(cursor.getY())) return false;
        LevelChunk chunk = LoadedChunks.chunk(level, cursor);
        return chunk != null && action.apply(level, cursor, chunk.getBlockState(cursor));
    }

    private static void checkThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("区域方块操作须在服务器线程运行");
    }

    /** 操作单个已加载方块。回调在服务器线程运行，坐标在返回后会被复用。 */
    @FunctionalInterface
    public interface Action {

        /** 返回真计入成功数。异常由调用方或所属调度器处理。 */
        boolean apply(ServerLevel level, BlockPos position, BlockState state);
    }
}
