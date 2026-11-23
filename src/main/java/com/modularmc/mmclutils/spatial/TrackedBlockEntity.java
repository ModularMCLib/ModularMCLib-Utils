package com.modularmc.mmclutils.spatial;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface TrackedBlockEntity {

    int mmclutils$probedMask();

    void mmclutils$setProbedMask(int mask);
}
