package com.modularmc.mmclutils.mixin.level;

import com.modularmc.mmclutils.api.level.LoadedChunkSource;
import com.modularmc.mmclutils.api.level.RecentChunks;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin implements LoadedChunkSource {

    @Shadow
    @Final
    private Thread mainThread;

    @Unique
    private final RecentChunks<LevelChunk> mmclutils$recent = new RecentChunks<>();

    @Shadow
    public abstract @Nullable LevelChunk getChunkNow(int chunkX, int chunkZ);

    @Invoker("getVisibleChunkIfPresent")
    protected abstract @Nullable ChunkHolder mmclutils$visibleChunk(long chunkPos);

    @Override
    public @Nullable LevelChunk mmclutils$loadedChunk(int chunkX, int chunkZ) {
        if (Thread.currentThread() == mainThread) return getChunkNow(chunkX, chunkZ);
        long key = ChunkPos.pack(chunkX, chunkZ);
        LevelChunk cached = mmclutils$recent.get(key);
        if (cached != null) return cached;
        ChunkHolder holder = mmclutils$visibleChunk(key);
        if (holder == null) return null;
        ChunkResult<LevelChunk> result = holder.getFullChunkFuture().getNow(null);
        if (result == null) return null;
        LevelChunk chunk = result.orElse(null);
        if (chunk == null) return null;
        mmclutils$recent.put(key, chunk);
        return chunk;
    }

    @Inject(method = "clearCache", at = @At("TAIL"))
    private void mmclutils$clearRecent(CallbackInfo ci) {
        mmclutils$recent.clear();
    }
}
