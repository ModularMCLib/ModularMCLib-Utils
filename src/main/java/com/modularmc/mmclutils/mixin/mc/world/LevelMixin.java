package com.modularmc.mmclutils.mixin.mc.world;

import com.modularmc.mmclutils.api.level.LevelStore.SlotHolder;

import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Level.class)
public abstract class LevelMixin implements SlotHolder {

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
