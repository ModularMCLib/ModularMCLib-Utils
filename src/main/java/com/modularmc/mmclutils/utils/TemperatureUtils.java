package com.modularmc.mmclutils.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** 估算方块与实体的相对温度，并格式化有明确单位的温度。 */
public final class TemperatureUtils {

    private TemperatureUtils() {}

    /** 根据方块、流体及发光状态估算相对热度。该值可作为颜色映射的输入。 */
    public static int temperature(BlockState state, Level level, BlockPos position) {
        if (state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK)) return 255;
        if (state.is(BlockTags.ICE) || state.is(BlockTags.SNOW) || state.getFluidState().is(FluidTags.WATER)) return 12;
        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) return 235;
        int light = state.getLightEmission(level, position);
        if (light > 0) return 128 + light * 7;
        int ambient = ambient(level, position);
        if (state.is(BlockTags.LEAVES)) return ambient - 8;
        if (state.is(BlockTags.SAND)) return ambient + 6;
        return ambient;
    }

    /** 用此位置的原版群系温度估算环境热度。调用方负责在所属线程读取世界。 */
    public static int ambient(Level level, BlockPos position) {
        return 86 + Math.round(Mth.clamp(level.getBiome(position).value().getBaseTemperature(), -0.5f, 2) * 22);
    }

    /** 估算实体的相对热度。燃烧状态优先，普通非生物使用环境热度。 */
    public static int temperature(Entity entity) {
        return entity.isOnFire() ? 255 : entity instanceof LivingEntity ? 218 : ambient(entity.level(), entity.blockPosition());
    }

    /** 判断实体本身是否作为热源。 */
    public static boolean hasHeat(Entity entity) {
        return entity instanceof LivingEntity || entity.isOnFire();
    }

    /** 输入以开尔文计量，按调用方选择显示 K 或 °C。 */
    public static String formatKelvin(double kelvin, boolean celsius) {
        return celsius ? NumberUtils.formatTwoDecimals(kelvin - 273.15) + " °C" : NumberUtils.formatTwoDecimals(kelvin) + " K";
    }
}
