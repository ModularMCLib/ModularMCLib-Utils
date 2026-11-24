package com.modularmc.mmclutils.api.shape;

import com.modularmc.mmclutils.mixin.mc.VoxelShapeAccessor;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectLinkedOpenHashMap;
import org.jspecify.annotations.Nullable;

/** 在所属线程按形状实例缓存采样结果。坐标复制和网格读取均按时限分步进行。 */
public final class VoxelShapeCache {

    private static final long MAX_CELLS = 65_536;
    private static final int MAX_COORDINATES = 4_096;
    private static final int MAX_CACHED_SHAPES = 128;
    private final Reference2ObjectLinkedOpenHashMap<VoxelShape, Capture> shapes = new Reference2ObjectLinkedOpenHashMap<>();

    /**
     * 推进指定形状的采集。再次传入相同形状实例可接着处理未完成的数据。
     *
     * @param shape    在所属世界线程访问的体素形状
     * @param deadline 以 {@link System#nanoTime()} 为基准的采集截止时刻
     * @return 已完成的数据，超过复杂度限制时返回 {@link VoxelShapeGeometry#UNSUPPORTED}，未采完时返回空值
     */
    public @Nullable VoxelShapeGeometry capture(VoxelShape shape, long deadline) {
        if (shape == Shapes.block()) return VoxelShapeGeometry.BLOCK;
        if (shape == Shapes.empty()) return VoxelShapeGeometry.EMPTY;
        Capture capture = shapes.getAndMoveToLast(shape);
        if (capture == null) {
            capture = new Capture(shape);
            shapes.put(shape, capture);
            if (shapes.size() > MAX_CACHED_SHAPES) shapes.removeFirst();
        }
        return capture.advance(deadline);
    }

    /** 丢弃正在处理和已采集的形状。已经交给调用方的几何数据仍可读取。 */
    public void clear() {
        shapes.clear();
    }

    private static final class Capture {

        private final DiscreteVoxelShape grid;
        private final DoubleList[] source;
        private final double[][] coordinates;
        private final VoxelOccupancy runs = new VoxelOccupancy();
        private final long cells;
        private int axis;
        private int coordinate;
        private long cell;
        private long start = -1;
        private @Nullable VoxelShapeGeometry result;

        private Capture(VoxelShape shape) {
            grid = ((VoxelShapeAccessor) shape).mmclutils$voxelGrid();
            source = new DoubleList[] { shape.getCoords(Direction.Axis.X), shape.getCoords(Direction.Axis.Y), shape.getCoords(Direction.Axis.Z) };
            cells = Math.multiplyExact(Math.multiplyExact((long) grid.getXSize(), grid.getYSize()), grid.getZSize());
            if (cells > MAX_CELLS || source[0].size() > MAX_COORDINATES || source[1].size() > MAX_COORDINATES || source[2].size() > MAX_COORDINATES) {
                coordinates = new double[][] { new double[] { 0 }, new double[] { 0 }, new double[] { 0 } };
                result = VoxelShapeGeometry.UNSUPPORTED;
            } else {
                coordinates = new double[][] { new double[source[0].size()], new double[source[1].size()], new double[source[2].size()] };
            }
        }

        private @Nullable VoxelShapeGeometry advance(long deadline) {
            if (result != null) return result;
            while (axis < 3) {
                if (System.nanoTime() >= deadline) return null;
                double value = source[axis].getDouble(coordinate);
                if (!Double.isFinite(value)) throw new IllegalArgumentException("体素形状包含非有限坐标");
                coordinates[axis][coordinate++] = value;
                if (coordinate == coordinates[axis].length) {
                    coordinate = 0;
                    axis++;
                }
            }
            while (cell < cells) {
                if ((cell & 63) == 0 && System.nanoTime() >= deadline) return null;
                int z = (int) (cell % grid.getZSize());
                long row = cell / grid.getZSize();
                boolean filled = grid.isFull((int) (row / grid.getYSize()), (int) (row % grid.getYSize()), z);
                if (filled && start < 0) start = cell;
                else if (!filled && start >= 0) {
                    runs.append(start);
                    runs.append(cell);
                    start = -1;
                }
                cell++;
            }
            if (start >= 0) {
                runs.append(start);
                runs.append(cells);
            }
            if (runs.empty()) result = VoxelShapeGeometry.EMPTY;
            else if (runs.fills(cells)) result = new VoxelShapeGeometry(bounds(coordinates[0]), bounds(coordinates[1]), bounds(coordinates[2]), VoxelShapeGeometry.BLOCK.runs());
            else result = new VoxelShapeGeometry(coordinates[0], coordinates[1], coordinates[2], runs);
            return result;
        }

        private static double[] bounds(double[] coordinates) {
            return new double[] { coordinates[0], coordinates[coordinates.length - 1] };
        }
    }
}
