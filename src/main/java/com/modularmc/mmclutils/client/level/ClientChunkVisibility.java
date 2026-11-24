package com.modularmc.mmclutils.client.level;

import com.modularmc.mmclutils.api.blockentity.BlockEntityTracking;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import org.jetbrains.annotations.ApiStatus;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * 客户端区块缓存移动视野时，原版只改视野中心，落到范围外的区块要等槽位被复用才卸载，缩小视野半径时被丢弃的区块不会卸载；
 * 这里让索引跟原版的可见范围一致：范围外的区块里的方块实体先从索引里摘掉，回到范围内再登记。
 */
@ApiStatus.Internal
public final class ClientChunkVisibility {

    /** 为客户端区块缓存注入提供当前槽位和可见范围。只在客户端区块线程使用。 */
    public interface View {

        /** 返回原版区块槽位，仅在本次同步期间读取。 */
        AtomicReferenceArray<LevelChunk> mmclutils$chunks();

        /** 查询指定区块坐标是否落在当前视野范围内。 */
        boolean mmclutils$inRange(int x, int z);
    }

    private ClientChunkVisibility() {}

    /** 视野存储扩缩后移除已被淘汰区块的索引，再按新范围同步。 */
    public static void afterResize(View now, View old) {
        if (old != now) {
            Set<LevelChunk> kept = Collections.newSetFromMap(new IdentityHashMap<>());
            AtomicReferenceArray<LevelChunk> current = now.mmclutils$chunks();
            for (int i = 0, n = current.length(); i < n; i++) {
                LevelChunk chunk = current.get(i);
                if (chunk != null) kept.add(chunk);
            }
            AtomicReferenceArray<LevelChunk> dropped = old.mmclutils$chunks();
            for (int i = 0, n = dropped.length(); i < n; i++) {
                LevelChunk chunk = dropped.get(i);
                if (chunk != null && !kept.contains(chunk)) BlockEntityTracking.setVisible(chunk, false);
            }
        }
        sync(now);
    }

    /** 按当前视野范围切换区块内方块实体的索引状态。 */
    public static void sync(View view) {
        AtomicReferenceArray<LevelChunk> chunks = view.mmclutils$chunks();
        for (int i = 0, n = chunks.length(); i < n; i++) {
            LevelChunk chunk = chunks.get(i);
            if (chunk == null) continue;
            ChunkPos pos = chunk.getPos();
            BlockEntityTracking.setVisible(chunk, view.mmclutils$inRange(pos.x, pos.z));
        }
    }
}
