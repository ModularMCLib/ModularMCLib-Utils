package com.modularmc.mmclutils.spatial;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/** 用维度标识和打包的方块坐标构造全局位置。 */
public final class GlobalPositions {

    private GlobalPositions() {}

    public static GlobalPos of(Identifier dimension, long packedPos) {
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(packedPos));
    }
}
