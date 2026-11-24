package com.modularmc.mmclutils.mixin.spatial;

import com.modularmc.mmclutils.api.blockentity.TrackedBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin implements TrackedBlockEntity {

    @Unique
    private int mmclutils$probed;

    @Override
    public int mmclutils$probedMask() {
        return mmclutils$probed;
    }

    @Override
    public void mmclutils$setProbedMask(int mask) {
        mmclutils$probed = mask;
    }
}
