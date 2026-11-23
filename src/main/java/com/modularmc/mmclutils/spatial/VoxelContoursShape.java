package com.modularmc.mmclutils.spatial;

import com.modularmc.mmclutils.client.level.SectionOccupancy;

import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Arrays;

/** Owned coordinates and occupied runs, safely published after capture and never mutated by workers. */
@OnlyIn(Dist.CLIENT)
public record VoxelContoursShape(double[] x, double[] y, double[] z, SectionOccupancy runs) {

    public static final VoxelContoursShape EMPTY = new VoxelContoursShape(new double[] { 0 }, new double[] { 0 }, new double[] { 0 }, new SectionOccupancy());
    public static final VoxelContoursShape UNSUPPORTED = new VoxelContoursShape(new double[] { 0 }, new double[] { 0 }, new double[] { 0 }, new SectionOccupancy());
    public static final VoxelContoursShape BLOCK = box(1);

    public static VoxelContoursShape box(double height) {
        var runs = new SectionOccupancy();
        runs.append(0);
        runs.append(1);
        return new VoxelContoursShape(new double[] { 0, 1 }, new double[] { 0, height }, new double[] { 0, 1 }, runs);
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
