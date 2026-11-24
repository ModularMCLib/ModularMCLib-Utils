package com.modularmc.mmclutils.client.level;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunk;

import lombok.Getter;

/**
 * 以中心区块为起点螺旋遍历，按高度交替访问已加载的非空区段。
 * 游标在同一客户端世界线程使用，跨调用只保留区块坐标和区段进度。
 */
public final class LoadedSectionCursor {

    /** 当前调用未取得区段的返回值。可用 {@link #isFinished()} 区分时限用尽与遍历完成。 */
    public static final long NO_SECTION = Long.MIN_VALUE;
    private final int centerX;
    private final int centerZ;
    private final int centerY;
    private int remaining;
    private int x;
    private int z;
    private int dx;
    private int dz = -1;
    private int chunkX;
    private int chunkZ;
    private int sectionCursor = -1;
    /** 此游标是否已经遍历完全部列。 */
    @Getter
    private boolean finished;

    /** 指定中心区段和水平搜索半径。坐标和半径均以区段为单位。 */
    public LoadedSectionCursor(int centerX, int centerY, int centerZ, int radius) {
        if (radius < 0) throw new IllegalArgumentException("遍历半径须为非负数");
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        int width = Math.addExact(Math.multiplyExact(radius, 2), 1);
        remaining = Math.multiplyExact(width, width);
    }

    /** 返回区段的打包坐标。到达时限或遍历完毕时返回 NO_SECTION。 */
    public long next(ClientLevel level, long deadline) {
        var source = level.getChunkSource();
        LevelChunk chunk = sectionCursor < 0 ? null : source.getChunk(chunkX, chunkZ, false);
        while (System.nanoTime() < deadline) {
            if (chunk == null) {
                if (remaining == 0) {
                    finished = true;
                    return NO_SECTION;
                }
                chunkX = centerX + x;
                chunkZ = centerZ + z;
                chunk = source.getChunk(chunkX, chunkZ, false);
                remaining--;
                if (x == z || x < 0 && x == -z || x > 0 && x == 1 - z) {
                    int previous = dx;
                    dx = -dz;
                    dz = previous;
                }
                x += dx;
                z += dz;
                sectionCursor = chunk == null ? -1 : 0;
                if (chunk == null) continue;
            }
            int count = chunk.getSections().length;
            if (sectionCursor >= count * 2 - 1) {
                chunk = null;
                sectionCursor = -1;
                continue;
            }
            int delta = (sectionCursor + 1) / 2 * ((sectionCursor & 1) == 0 ? -1 : 1);
            sectionCursor++;
            int index = Math.clamp(level.getSectionIndexFromSectionY(centerY), 0, count - 1) + delta;
            if (index < 0 || index >= count || chunk.getSection(index).hasOnlyAir()) continue;
            return SectionPos.asLong(chunkX, level.getSectionYFromSectionIndex(index), chunkZ);
        }
        return NO_SECTION;
    }
}
