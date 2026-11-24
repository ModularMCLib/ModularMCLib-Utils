package com.modularmc.mmclutils.mixin.spatial;

import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 供采集线程复制体素占用数据。 */
@NullMarked
@Mixin(VoxelShape.class)
public interface VoxelShapeAccessor {

    /**
     * 返回原版体素网格。调用方在拥有该形状的线程上读取网格。
     */
    @Accessor("shape")
    DiscreteVoxelShape mmclutils$voxelGrid();
}
