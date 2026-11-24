package com.modularmc.mmclutils.utils;

import com.modularmc.mmclutils.api.level.LoadedChunks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Function;

/** 世界的日照、相邻方块查询和积雪操作。位置查询只使用已加载区块。 */
public final class LevelUtils {

    private static final Direction[] DIRECTIONS = Direction.values();

    private LevelUtils() {}

    /** 判断当前位置是否处于晴朗日照下。区块未加载或维度无天空光时返回假。 */
    public static boolean canSeeSunClearly(Level level, BlockPos pos) {
        return level.dimensionType().hasSkyLight() && level.isBrightOutside() && !level.isRaining() && !level.isThundering() && LoadedChunks.isLoaded(level, pos) && level.canSeeSky(pos);
    }

    /** 将世界日时映射成 0～1 的日照系数。白天沿正弦曲线变化，夜间为零。 */
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

    /** 统计位置六面已加载的指定方块。 */
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

    /** 判断状态是否为原版积雪层或雪块。 */
    public static boolean isSnow(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK);
    }

    /**
     * 在服务端线程移除一层雪。雪块会变成七层雪，最后一层移除时没有掉落物。
     *
     * @param level     所属维度
     * @param position  待读取的已加载方块位置
     * @param playSound 成功移除后是否播放熄灭音效
     * @return 方块是否发生变化
     */
    public static boolean removeSnowLayer(ServerLevel level, BlockPos position, boolean playSound) {
        BlockState state = LoadedChunks.blockState(level, position);
        if (!isSnow(state)) return false;
        int layers = state.is(Blocks.SNOW_BLOCK) ? 8 : state.getValue(SnowLayerBlock.LAYERS);
        boolean changed = layers == 1 ? level.destroyBlock(position, false) : level.setBlock(position, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers - 1), Block.UPDATE_ALL_IMMEDIATE);
        if (changed && playSound) level.playSound(null, position, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1, 1);
        return changed;
    }
}
