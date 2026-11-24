package com.modularmc.mmclutils.mixin.mc.world;

import com.modularmc.mmclutils.api.blockentity.BlockEntityTracking.TrackedBlockEntity;

import net.minecraft.world.level.block.entity.BlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 在原版方块实体中保存由方块实体索引管理的探测位掩码。 */
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
