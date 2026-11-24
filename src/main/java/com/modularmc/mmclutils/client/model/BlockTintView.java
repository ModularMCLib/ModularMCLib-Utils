package com.modularmc.mmclutils.client.model;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

import org.jspecify.annotations.Nullable;

/** 以调用方提供的方块状态替代一格数据，维度范围、颜色和光照沿用底层视图。 */
public record BlockTintView(BlockAndTintGetter parent, long position, BlockState state, @Nullable BlockEntity entity)
        implements BlockAndTintGetter {

    public BlockTintView(BlockAndTintGetter parent, BlockPos position, BlockState state, @Nullable BlockEntity entity) {
        this(parent, position.asLong(), state, entity);
    }

    @Override
    public CardinalLighting cardinalLighting() {
        return parent.cardinalLighting();
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return parent.getLightEngine();
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return parent.getBlockTint(pos, resolver);
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return pos.asLong() == position ? entity : parent.getBlockEntity(pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return pos.asLong() == position ? state : parent.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return pos.asLong() == position ? state.getFluidState() : parent.getFluidState(pos);
    }

    @Override
    public int getHeight() {
        return parent.getHeight();
    }

    @Override
    public int getMinY() {
        return parent.getMinY();
    }
}
