package com.modularmc.mmclutils.client.render.quad;

import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.neoforged.neoforge.client.model.quad.BakedNormals;

import org.joml.Vector3f;
import org.joml.Vector3fc;

/** 计算四边形的法线、光照面和对齐情况。 */
public final class QuadGeometry {

    private QuadGeometry() {}

    public static Vector3f vertexNormal(QuadView quad, int vertex, Vector3f output) {
        int packed = quad.packedNormal(vertex);
        if (packed != 0) {
            BakedNormals.unpack(packed, output);
            return output;
        }
        Direction face = quad.lightFace();
        return output.set(face.getStepX(), face.getStepY(), face.getStepZ());
    }

    /** 按顶点顺序计算面法线。退化的面返回零向量。 */
    public static Vector3f normal(QuadView quad, Vector3f result) {
        Vector3fc a = quad.position(0), b = quad.position(1), c = quad.position(2), d = quad.position(3);
        float x = d.x() - b.x(), y = d.y() - b.y(), z = d.z() - b.z();
        result.set(c).sub(a).cross(x, y, z);
        return result.lengthSquared() == 0 ? result : result.normalize();
    }

    public static Direction lightFace(Vector3fc normal) {
        float x = Math.abs(normal.x()), y = Math.abs(normal.y()), z = Math.abs(normal.z());
        if (x > y && x >= z) return normal.x() > 0 ? Direction.EAST : Direction.WEST;
        if (z > y) return normal.z() > 0 ? Direction.SOUTH : Direction.NORTH;
        return normal.y() < 0 ? Direction.DOWN : Direction.UP;
    }

    /** 判断四个顶点是否落在垂直于指定轴的同一平面内。 */
    public static boolean isParallel(QuadView quad, Direction.Axis axis, float tolerance) {
        Vector3fc first = quad.position(0);
        double coordinate = axis.choose(first.x(), first.y(), first.z());
        for (int i = 1; i < QuadView.VERTEX_COUNT; i++) {
            Vector3fc point = quad.position(i);
            if (Math.abs(axis.choose(point.x(), point.y(), point.z()) - coordinate) > tolerance) return false;
        }
        return true;
    }

    /** 用源四边形的两个三角形重算目标顶点的颜色与 UV。两个视图须来自同一平面。 */
    public static void interpolate(QuadView source, MutableQuadView target, Direction.Axis droppedAxis) {
        int x = droppedAxis == Direction.Axis.X ? 1 : 0;
        int y = droppedAxis == Direction.Axis.Z ? 1 : 2;
        for (int i = 0; i < QuadView.VERTEX_COUNT; i++) {
            Vector3fc point = target.position(i);
            if (!sample(source, target, i, point.get(x), point.get(y), x, y, 1, 2, false)) {
                sample(source, target, i, point.get(x), point.get(y), x, y, 2, 3, true);
            }
        }
    }

    private static boolean sample(QuadView source, MutableQuadView target, int index, float px, float py,
                                  int axisX, int axisY, int b, int c, boolean extend) {
        Vector3fc origin = source.position(0), v1 = source.position(b), v2 = source.position(c);
        float x1 = v1.get(axisX) - origin.get(axisX), y1 = v1.get(axisY) - origin.get(axisY);
        float x2 = v2.get(axisX) - origin.get(axisX), y2 = v2.get(axisY) - origin.get(axisY);
        float x = px - origin.get(axisX), y = py - origin.get(axisY);
        float area = x1 * y2 - y1 * x2;
        if (area == 0) return false;
        float w1 = (x * y2 - y * x2) / area, w2 = (x1 * y - y1 * x) / area;
        float w0 = 1 - w1 - w2;
        if (!extend && Math.min(w0, Math.min(w1, w2)) < -0.00001f) return false;
        target.uv(index, w0 * source.u(0) + w1 * source.u(b) + w2 * source.u(c),
                w0 * source.v(0) + w1 * source.v(b) + w2 * source.v(c));
        int aColor = source.color(0), bColor = source.color(b), cColor = source.color(c);
        target.color(index, ARGB.color(channel(aColor, bColor, cColor, 24, w0, w1, w2),
                channel(aColor, bColor, cColor, 16, w0, w1, w2), channel(aColor, bColor, cColor, 8, w0, w1, w2),
                channel(aColor, bColor, cColor, 0, w0, w1, w2)));
        return true;
    }

    private static int channel(int a, int b, int c, int shift, float w0, float w1, float w2) {
        return Math.clamp(Math.round(w0 * (a >>> shift & 255) + w1 * (b >>> shift & 255) + w2 * (c >>> shift & 255)), 0, 255);
    }
}
