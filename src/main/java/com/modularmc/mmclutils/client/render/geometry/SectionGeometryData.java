package com.modularmc.mmclutils.client.render.geometry;

import com.modularmc.mmclutils.api.shape.VoxelShapeGeometry;

/** 区段和一格邻接范围的几何数据。构造时接管各数组，交付后调用方和后台线程均只读。 */
public record SectionGeometryData(long origin, int modes, VoxelShapeGeometry[] shapes, int[] temperatures, int[] visibleFaces) {

    public static final int INCLUDE_TEMPERATURE = 1;

    public VoxelShapeGeometry shape(int x, int y, int z) {
        return x < -1 || y < -1 || z < -1 || x > 16 || y > 16 || z > 16 ? VoxelShapeGeometry.EMPTY : shapes[index(x, y, z)];
    }

    public static int index(int x, int y, int z) {
        return ((y + 1) * 18 + z + 1) * 18 + x + 1;
    }
}
