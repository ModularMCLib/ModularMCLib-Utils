package com.modularmc.mmclutils.api.shape;

import net.minecraft.core.Direction;

import java.util.Arrays;

/** 构造时接管坐标和占用区间。读取方共享同一份数据，交付后各方均只读。 */
public record VoxelShapeGeometry(double[] x, double[] y, double[] z, VoxelOccupancy runs) {

    public static final VoxelShapeGeometry EMPTY = new VoxelShapeGeometry(new double[] { 0 }, new double[] { 0 }, new double[] { 0 }, new VoxelOccupancy());
    public static final VoxelShapeGeometry UNSUPPORTED = new VoxelShapeGeometry(new double[] { 0 }, new double[] { 0 }, new double[] { 0 }, new VoxelOccupancy());
    public static final VoxelShapeGeometry BLOCK = box(1);

    public static VoxelShapeGeometry box(double height) {
        var runs = new VoxelOccupancy();
        runs.append(0);
        runs.append(1);
        return new VoxelShapeGeometry(new double[] { 0, 1 }, new double[] { 0, height }, new double[] { 0, 1 }, runs);
    }

    public double[] coordinates(Direction.Axis axis) {
        return switch (axis) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
    }

    public boolean empty() {
        return runs.empty();
    }

    public boolean filled(int cx, int cy, int cz) {
        if (cx < 0 || cy < 0 || cz < 0 || cx >= x.length - 1 || cy >= y.length - 1 || cz >= z.length - 1) return false;
        long cell = ((long) cx * (y.length - 1) + cy) * (z.length - 1) + cz;
        return runs.contains(cell);
    }

    public boolean contains(double px, double py, double pz) {
        return filled(cell(x, px), cell(y, py), cell(z, pz));
    }

    private static int cell(double[] coordinates, double position) {
        int index = Arrays.binarySearch(coordinates, position);
        return index >= 0 ? index : -index - 2;
    }
}
