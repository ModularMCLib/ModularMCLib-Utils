package com.modularmc.mmclutils.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** 与世界、维度和天气有关的只读计算。 */
public final class LevelUtils {

    private LevelUtils() {}

    public static boolean canSeeSunClearly(Level level, BlockPos pos) {
        return level.dimensionType().hasSkyLight() && level.canSeeSky(pos) && !level.isRaining() && !level.isThundering();
    }

    public static double sunIntensity(long dayTime) {
        long time = Math.floorMod(dayTime, 24000);
        return time < 12000 ? Math.sin(Math.PI * time / 12000) : 0;
    }
}
