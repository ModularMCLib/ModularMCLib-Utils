package com.modularmc.mmclutils.api.blockentity;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface TrackedChunk {

    boolean mmclutils$hidden();

    void mmclutils$setHidden(boolean hidden);
}
