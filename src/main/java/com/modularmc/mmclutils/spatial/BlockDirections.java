package com.modularmc.mmclutils.spatial;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** 将局部方向、点击位置与打包坐标映射到原版方块面。 */
public final class BlockDirections {

    private BlockDirections() {}

    public static long offset(long packedPosition, Direction side) {
        return BlockPos.offset(packedPosition, side);
    }

    public static Direction nearestSide(Vec3 point, BlockPos position) {
        return Direction.getApproximateNearest(point.x - position.getX() - .5,
                point.y - position.getY() - .5, point.z - position.getZ() - .5);
    }

    public static Direction rotateHorizontal(Direction direction, int quarterTurns) {
        if (direction.getAxis().isVertical()) return direction;
        return Direction.from2DDataValue(Math.floorMod(direction.get2DDataValue() + quarterTurns, 4));
    }
}
