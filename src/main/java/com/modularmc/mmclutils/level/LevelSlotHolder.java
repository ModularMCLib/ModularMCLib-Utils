package com.modularmc.mmclutils.level;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface LevelSlotHolder {

    @Nullable
    Object[] mmclutils$slots();

    void mmclutils$setSlots(@Nullable Object[] slots);

    boolean mmclutils$released();

    void mmclutils$setReleased(boolean released);
}
