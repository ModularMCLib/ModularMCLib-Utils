package com.modularmc.mmclutils.mixin.level;

import com.modularmc.mmclutils.level.LevelSlotHolder;

import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Level.class)
public abstract class LevelMixin implements LevelSlotHolder {

    @Unique
    private volatile @Nullable Object[] mmclutils$levelSlots;

    @Unique
    private volatile boolean mmclutils$levelReleased;

    @Override
    public @Nullable Object[] mmclutils$slots() {
        return mmclutils$levelSlots;
    }

    @Override
    public void mmclutils$setSlots(@Nullable Object[] slots) {
        mmclutils$levelSlots = slots;
    }

    @Override
    public boolean mmclutils$released() {
        return mmclutils$levelReleased;
    }

    @Override
    public void mmclutils$setReleased(boolean released) {
        mmclutils$levelReleased = released;
    }
}
