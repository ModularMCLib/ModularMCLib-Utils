package com.modularmc.mmclutils.api.blockentity;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface TrackedBlockEntity {

    int mmclutils$probedMask();

    void mmclutils$setProbedMask(int mask);
}
