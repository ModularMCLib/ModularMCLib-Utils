package com.modularmc.mmclutils.utils;

import com.modularmc.mmclutils.api.level.LoadedChunks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.function.Function;

/** 与世界、维度和天气有关的只读计算。 */
public final class LevelUtils {

    private static final Direction[] DIRECTIONS = Direction.values();

    private LevelUtils() {}

    public static boolean canSeeSunClearly(Level level, BlockPos pos) {
        return level.dimensionType().hasSkyLight() && level.isBrightOutside() && !level.isRaining() && !level.isThundering() && LoadedChunks.isLoaded(level, pos) && level.canSeeSky(pos);
    }

    public static double sunIntensity(long dayTime) {
        long time = Math.floorMod(dayTime, 24000);
        return time < 12000 ? Math.sin(Math.PI * time / 12000) : 0;
    }

    /** 从调用方提供的六面数据中统计指定方块。 */
    public static int countAdjacentBlocks(Function<Direction, Block> adjacent, Block block) {
        int count = 0;
        for (Direction direction : DIRECTIONS) {
            if (adjacent.apply(direction) == block) count++;
        }
        return count;
    }

    public static int countAdjacentBlocks(Level level, BlockPos position, Block block) {
        return countAdjacentBlocks(level, position.asLong(), block);
    }

    /** 统计已加载区块中的相邻方块，查询范围为当前世界的建造高度。 */
    public static int countAdjacentBlocks(Level level, long position, Block block) {
        MutableBlockPos cursor = new MutableBlockPos();
        int count = 0;
        for (Direction direction : DIRECTIONS) {
            cursor.set(BlockPos.offset(position, direction));
            if (level.isOutsideBuildHeight(cursor)) continue;
            var chunk = LoadedChunks.chunk(level, cursor);
            if (chunk != null && chunk.getBlockState(cursor).is(block)) count++;
        }
        return count;
    }
}
