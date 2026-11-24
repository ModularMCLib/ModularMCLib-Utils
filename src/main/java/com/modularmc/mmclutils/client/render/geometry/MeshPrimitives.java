package com.modularmc.mmclutils.client.render.geometry;

import net.minecraft.core.Direction.Axis;

/** 面向顶点流的基础几何生成。顶点按四边形或线段的顺序输出。 */
public final class MeshPrimitives {

    private static final int[] BOX_CORNERS = {
            0, 1, 5, 4, 2, 6, 7, 3, 0, 2, 3, 1,
            4, 5, 7, 6, 0, 4, 6, 2, 1, 3, 7, 5
    };

    private MeshPrimitives() {}

    /** 依次写出下、上、北、南、西、东六个朝外的面，每面四个顶点。 */
    public static void box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, VertexSink sink) {
        for (int corner : BOX_CORNERS) {
            sink.vertex((corner & 1) == 0 ? minX : maxX, (corner & 2) == 0 ? minY : maxY, (corner & 4) == 0 ? minZ : maxZ);
        }
    }

    /** 以原点为球心写出四边形球面。纬度分段至少为二，经度分段至少为三。 */
    public static void sphere(float radius, int latitudes, int longitudes, VertexSink sink) {
        if (latitudes < 2 || longitudes < 3) {
            throw new IllegalArgumentException("球面分段数不足");
        }
        float[] cosines = new float[longitudes];
        float[] sines = new float[longitudes];
        for (int longitude = 0; longitude < longitudes; longitude++) {
            double angle = longitude * (Math.TAU / longitudes);
            cosines[longitude] = (float) Math.cos(angle);
            sines[longitude] = (float) Math.sin(angle);
        }
        float ring = 0;
        float y = -radius;
        for (int latitude = 1; latitude <= latitudes; latitude++) {
            double angle = latitude * (Math.PI / latitudes) - Math.PI / 2;
            float nextRing = latitude == latitudes ? 0 : radius * (float) Math.cos(angle);
            float nextY = radius * (float) Math.sin(angle);
            for (int longitude = 0; longitude < longitudes; longitude++) {
                int next = longitude + 1 == longitudes ? 0 : longitude + 1;
                sink.vertex(ring * cosines[longitude], y, ring * sines[longitude]);
                sink.vertex(nextRing * cosines[longitude], nextY, nextRing * sines[longitude]);
                sink.vertex(nextRing * cosines[next], nextY, nextRing * sines[next]);
                sink.vertex(ring * cosines[next], y, ring * sines[next]);
            }
            ring = nextRing;
            y = nextY;
        }
    }

    /** 写出垂直于给定轴的闭合圆，每条线段写入两个顶点。 */
    public static void circle(Axis axis, float radius, int segments, VertexSink sink) {
        if (segments < 3) throw new IllegalArgumentException("圆的分段数至少为三");
        float x = radius, y = 0;
        for (int segment = 1; segment <= segments; segment++) {
            double angle = segment * (Math.TAU / segments);
            float nextX = segment == segments ? radius : radius * (float) Math.cos(angle);
            float nextY = segment == segments ? 0 : radius * (float) Math.sin(angle);
            planarVertex(axis, x, y, sink);
            planarVertex(axis, nextX, nextY, sink);
            x = nextX;
            y = nextY;
        }
    }

    private static void planarVertex(Axis axis, float x, float y, VertexSink sink) {
        switch (axis) {
            case X -> sink.vertex(0, x, y);
            case Y -> sink.vertex(x, 0, y);
            case Z -> sink.vertex(x, y, 0);
        }
    }

    /** 在生成几何的调用线程上接收连续顶点，可直接写入调用者的缓冲。 */
    @FunctionalInterface
    public interface VertexSink {

        /** 写入一个顶点。生成方法决定每组顶点的图元类型和顺序。 */
        void vertex(float x, float y, float z);
    }
}
