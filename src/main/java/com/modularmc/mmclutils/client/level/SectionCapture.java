package com.modularmc.mmclutils.client.level;

import com.modularmc.mmclutils.level.BlockEnvironment;
import com.modularmc.mmclutils.spatial.BlockDirections;
import com.modularmc.mmclutils.spatial.VoxelContoursShape;
import com.modularmc.mmclutils.spatial.VoxelShapes;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/** 在客户端线程分步读取已加载区段。单个复杂形状的读取同样受本轮时限约束。 */
public final class SectionCapture {

    private static final Direction[] DIRECTIONS = Direction.values();
    private final long origin;
    private final int modes;
    private final int baseChunkX;
    private final int baseChunkZ;
    private final VoxelContoursShape[] shapes = new VoxelContoursShape[18 * 18 * 18];
    private final int[] temperatures = new int[4096];
    private final int[] visible = new int[4096];
    private final BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
    private final LevelChunk[] chunks = new LevelChunk[9];
    private @Nullable ClientLevel world;
    private @Nullable SectionGeometryData result;
    private @Nullable PendingBlock pending;
    private int queriedChunks;
    private int cursor;

    public SectionCapture(long section, int modes) {
        origin = BlockPos.asLong(SectionPos.sectionToBlockCoord(SectionPos.x(section)), SectionPos.sectionToBlockCoord(SectionPos.y(section)), SectionPos.sectionToBlockCoord(SectionPos.z(section)));
        baseChunkX = BlockPos.getX(origin) >> 4;
        baseChunkZ = BlockPos.getZ(origin) >> 4;
        this.modes = modes;
    }

    /** 在同一客户端世界线程恢复采集，时限使用 System.nanoTime 计时。 */
    public @Nullable SectionGeometryData advance(ClientLevel level, VoxelShapes cache, long deadline) {
        if (world == null) world = level;
        else if (world != level) throw new IllegalArgumentException("区段采集已经绑定到另一世界");
        if (result != null) return result;
        if (System.nanoTime() >= deadline) return null;
        refreshChunks(level);

        while (cursor < shapes.length && System.nanoTime() < deadline) {
            int x = cursor % 18 - 1, z = cursor / 18 % 18 - 1, y = cursor / (18 * 18) - 1;
            position.set(BlockPos.getX(origin) + x, BlockPos.getY(origin) + y, BlockPos.getZ(origin) + z);
            if (pending == null) {
                LevelChunk chunk = loadedChunk(level, position.getX() >> 4, position.getZ() >> 4);
                if (chunk == null) {
                    shapes[cursor++] = VoxelContoursShape.EMPTY;
                    continue;
                }
                BlockState state = chunk.getBlockState(position);
                if (state.isAir()) {
                    shapes[cursor++] = VoxelContoursShape.EMPTY;
                    continue;
                }
                pending = new PendingBlock(state, state.getShape(level, position));
            }
            BlockState state = pending.state();
            VoxelContoursShape shape = cache.capture(pending.shape(), deadline);
            if (shape == null) return null;
            pending = null;
            if (shape == VoxelContoursShape.UNSUPPORTED) {
                shapes[cursor++] = shape;
                continue;
            }
            var fluid = state.getFluidState();
            if (shape.empty() && !fluid.isEmpty()) shape = VoxelContoursShape.box(fluid.getHeight(level, position));
            shapes[cursor++] = shape;
            if (x < 0 || y < 0 || z < 0 || x > 15 || y > 15 || z > 15) continue;
            int index = (y * 16 + z) * 16 + x;
            visible[index] = exposedFaces(level, position, state);
            if ((modes & SectionGeometryData.INCLUDE_TEMPERATURE) != 0 && (visible[index] != 0 || shape != VoxelContoursShape.BLOCK)) temperatures[index] = BlockEnvironment.temperature(state, level, position);
        }
        if (cursor != shapes.length) return null;
        Arrays.fill(chunks, null);
        result = new SectionGeometryData(origin, modes, shapes, temperatures, visible);
        return result;
    }

    private int exposedFaces(ClientLevel level, BlockPos position, BlockState state) {
        int faces = 0;
        var fluid = state.getFluidState();
        for (Direction direction : DIRECTIONS) {
            neighbor.setWithOffset(position, direction);
            LevelChunk chunk = loadedChunk(level, neighbor.getX() >> 4, neighbor.getZ() >> 4);
            if (chunk == null) faces |= BlockDirections.mask(direction);
            else {
                BlockState adjacent = chunk.getBlockState(neighbor);
                if ((fluid.isEmpty() || !fluid.getType().isSame(adjacent.getFluidState().getType())) && Block.shouldRenderFace(level, position, state, adjacent, direction)) faces |= BlockDirections.mask(direction);
            }
        }
        return faces;
    }

    private @Nullable LevelChunk loadedChunk(ClientLevel level, int chunkX, int chunkZ) {
        int chunkIndex = (chunkX - baseChunkX + 1) * 3 + chunkZ - baseChunkZ + 1;
        int bit = 1 << chunkIndex;
        if ((queriedChunks & bit) == 0) {
            chunks[chunkIndex] = level.getChunkSource().getChunk(chunkX, chunkZ, false);
            queriedChunks |= bit;
        }
        return chunks[chunkIndex];
    }

    private void refreshChunks(ClientLevel level) {
        for (int mask = queriedChunks; mask != 0; mask &= mask - 1) {
            int index = Integer.numberOfTrailingZeros(mask);
            int x = baseChunkX + index / 3 - 1;
            int z = baseChunkZ + index % 3 - 1;
            if (chunks[index] == level.getChunkSource().getChunk(x, z, false)) continue;

            // 区块缺失或被替换后，已经采集的可见面也可能发生变化。
            cursor = 0;
            pending = null;
            queriedChunks = 0;
            Arrays.fill(chunks, null);
            Arrays.fill(temperatures, 0);
            Arrays.fill(visible, 0);
            return;
        }
    }

    private record PendingBlock(BlockState state, VoxelShape shape) {}
}
