package com.modularmc.mmclutils.api.shape;

import net.minecraft.core.Direction;

import java.util.Arrays;

/** 构造时接管坐标和占用区间。读取方共享同一份数据，交付后各方均只读。 */
public record VoxelShapeGeometry(double[] x, double[] y, double[] z, VoxelOccupancy runs) {

    /** 没有占用区间的空形状。 */
    public static final VoxelShapeGeometry EMPTY = new VoxelShapeGeometry(new double[] { 0 }, new double[] { 0 }, new double[] { 0 }, new VoxelOccupancy());
    /** 形状无法采集时的特殊结果。可按引用区分此状态与空形状。 */
    public static final VoxelShapeGeometry UNSUPPORTED = new VoxelShapeGeometry(new double[] { 0 }, new double[] { 0 }, new double[] { 0 }, new VoxelOccupancy());
    /** 局部坐标从零到一的完整立方体，调用方共享此几何数据。 */
    public static final VoxelShapeGeometry BLOCK = box(1);

    /** 创建底面覆盖一个完整方块的直角体，高度以方块为单位。高度应大于零。 */
    public static VoxelShapeGeometry box(double height) {
        var runs = new VoxelOccupancy();
        runs.append(0);
        runs.append(1);
        return new VoxelShapeGeometry(new double[] { 0, 1 }, new double[] { 0, height }, new double[] { 0, 1 }, runs);
    }

    /** 返回指定轴的分割坐标数组。数组由当前几何持有，调用方须只读访问。 */
    public double[] coordinates(Direction.Axis axis) {
        return switch (axis) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
    }

    /** 判断是否存在任何占用区间。 */
    public boolean empty() {
        return runs.empty();
    }

    /** 按三轴网格单元索引判断是否占用。越界单元视为空。 */
    public boolean filled(int cx, int cy, int cz) {
        if (cx < 0 || cy < 0 || cz < 0 || cx >= x.length - 1 || cy >= y.length - 1 || cz >= z.length - 1) return false;
        long cell = ((long) cx * (y.length - 1) + cy) * (z.length - 1) + cz;
        return runs.contains(cell);
    }

    /** 按局部坐标查找所在体素。每个单元包括下界，排除上界。 */
    public boolean contains(double px, double py, double pz) {
        return filled(cell(x, px), cell(y, py), cell(z, pz));
    }

    private static int cell(double[] coordinates, double position) {
        int index = Arrays.binarySearch(coordinates, position);
        return index >= 0 ? index : -index - 2;
    }
}
