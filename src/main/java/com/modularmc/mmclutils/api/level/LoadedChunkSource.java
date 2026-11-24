package com.modularmc.mmclutils.api.level;

import net.minecraft.world.level.chunk.LevelChunk;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

@ApiStatus.Internal
public interface LoadedChunkSource {

    @Nullable
    LevelChunk mmclutils$loadedChunk(int chunkX, int chunkZ);
}
