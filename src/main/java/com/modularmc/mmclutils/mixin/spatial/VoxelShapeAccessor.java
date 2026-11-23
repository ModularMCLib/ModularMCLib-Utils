package com.modularmc.mmclutils.mixin.spatial;

import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jspecify.annotations.NullMarked;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 供客户端线程复制体素占用数据。 */
@NullMarked
@Mixin(VoxelShape.class)
public interface VoxelShapeAccessor {

    /**
     * 返回原版体素网格，只在客户端采集时读取。
     */
    @Accessor("shape")
    DiscreteVoxelShape mmclutils$voxelGrid();
}
