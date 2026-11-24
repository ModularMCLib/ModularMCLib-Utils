package com.modularmc.mmclutils.client.level;

import com.modularmc.mmclutils.api.shape.VoxelShapeCache;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.function.LongConsumer;

/** 在客户端世界线程维护有界的区段采集缓存。采集模式变化时会替换同一区段的旧数据。 */
public final class SectionGeometryCache {

    private final ClientLevel level;
    private final int capacity;
    private final VoxelShapeCache shapes = new VoxelShapeCache();
    private final Long2ObjectLinkedOpenHashMap<Entry> sections = new Long2ObjectLinkedOpenHashMap<>();

    public SectionGeometryCache(ClientLevel level, int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("区段缓存容量必须为正数");
        this.level = level;
        this.capacity = capacity;
    }

    public @Nullable SectionGeometryData get(long section, int modes) {
        Entry entry = sections.getAndMoveToLast(section);
        return entry != null && entry.modes == modes ? entry.geometry : null;
    }

    /** 推进采集至指定时限。采集完成时转交可在后台读取的数据。 */
    public @Nullable SectionGeometryData advance(long section, int modes, long deadline) {
        Entry entry = sections.getAndMoveToLast(section);
        if (entry == null || entry.modes != modes) {
            if (System.nanoTime() >= deadline) return null;
            entry = new Entry(section, modes);
            sections.putAndMoveToLast(section, entry);
            if (sections.size() > capacity) sections.removeFirst();
        }
        SectionCapture capture = entry.capture;
        if (capture != null) {
            entry.geometry = capture.advance(level, shapes, deadline);
            if (entry.geometry != null) entry.capture = null;
        }
        return entry.geometry;
    }

    public void invalidate(long section) {
        sections.remove(section);
    }

    /** 方块变化后作废所有读取过该位置的区段。调用方可以同时取消相同键的后台任务。 */
    public void invalidateBlock(long position) {
        shapes.clear();
        forAffectedSections(position, this::invalidate);
    }

    public static void forAffectedSections(long position, LongConsumer action) {
        int x = BlockPos.getX(position), y = BlockPos.getY(position), z = BlockPos.getZ(position);
        for (int sx = (x - 1) >> 4; sx <= ((x + 1) >> 4); sx++) {
            for (int sy = (y - 1) >> 4; sy <= ((y + 1) >> 4); sy++) {
                for (int sz = (z - 1) >> 4; sz <= ((z + 1) >> 4); sz++) {
                    action.accept(SectionPos.asLong(sx, sy, sz));
                }
            }
        }
    }

    /** 区块加载、替换或移除时，作废本列及采集边界与其相交的区段。 */
    public void invalidateChunk(int x, int z) {
        var iterator = sections.keySet().iterator();
        while (iterator.hasNext()) {
            long section = iterator.nextLong();
            if (Math.abs(SectionPos.x(section) - x) <= 1 && Math.abs(SectionPos.z(section) - z) <= 1) iterator.remove();
        }
    }

    /** 世界或资源版本结束时释放全部采集状态。 */
    public void clear() {
        sections.clear();
        shapes.clear();
    }

    private static final class Entry {

        private final int modes;
        private @Nullable SectionCapture capture;
        private @Nullable SectionGeometryData geometry;

        private Entry(long section, int modes) {
            this.modes = modes;
            capture = new SectionCapture(section, modes);
        }
    }
}
