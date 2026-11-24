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

    public interface View {

        AtomicReferenceArray<LevelChunk> mmclutils$chunks();

        boolean mmclutils$inRange(int x, int z);
    }

    private ClientChunkVisibility() {}

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

    public static void sync(View view) {
        AtomicReferenceArray<LevelChunk> chunks = view.mmclutils$chunks();
        for (int i = 0, n = chunks.length(); i < n; i++) {
            LevelChunk chunk = chunks.get(i);
            if (chunk == null) continue;
            ChunkPos pos = chunk.getPos();
            BlockEntityTracking.setVisible(chunk, view.mmclutils$inRange(pos.x(), pos.z()));
        }
    }
}
