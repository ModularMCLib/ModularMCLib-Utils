package com.modularmc.mmclutils.client.render.geometry;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 在水平面上计算沿圆周扫过的径向线段。角度以弧度表示。 */
public final class OrbitGeometry {

    private OrbitGeometry() {}

    public static AABB bounds(Vec3 center, Sweep sweep) {
        AABB bounds = segmentBounds(center, sweep, sweep.startAngle());
        for (int i = 1; i <= sweep.steps(); i++) bounds = bounds.minmax(segmentBounds(center, sweep, sweep.angle(i)));
        return bounds.inflate(sweep.halfWidth(), sweep.halfHeight(), sweep.halfWidth());
    }

    public static boolean intersects(AABB target, Vec3 center, Sweep sweep) {
        AABB expanded = target.inflate(sweep.halfWidth(), sweep.halfHeight(), sweep.halfWidth());
        for (int i = 0; i <= sweep.steps(); i++) {
            double angle = sweep.angle(i);
            Vec3 start = point(center, angle, sweep.innerRadius());
            Vec3 end = point(center, angle, sweep.outerRadius());
            if (expanded.contains(start) || expanded.contains(end) || expanded.clip(start, end).isPresent()) return true;
        }
        return false;
    }

    private static AABB segmentBounds(Vec3 center, Sweep sweep, double angle) {
        return new AABB(point(center, angle, sweep.innerRadius()), point(center, angle, sweep.outerRadius()));
    }

    public static Vec3 point(Vec3 center, double angle, double radius) {
        return center.add(Math.cos(angle) * radius, 0, -Math.sin(angle) * radius);
    }

    /** 起止角度、径向端点以及水平和垂直扩张。交集和包围盒使用相同的采样点。 */
    public record Sweep(double startAngle, double endAngle, double innerRadius, double outerRadius,
                        double halfWidth, double halfHeight, int steps) {

        public Sweep {
            if (!Double.isFinite(startAngle) || !Double.isFinite(endAngle) || !Double.isFinite(innerRadius) || !Double.isFinite(outerRadius) || !Double.isFinite(halfWidth) || !Double.isFinite(halfHeight) || innerRadius < 0 || innerRadius > outerRadius || halfWidth < 0 || halfHeight < 0 || steps < 1) {
                throw new IllegalArgumentException("轨道几何参数超出范围");
            }
        }

        public double angle(int step) {
            double fraction = (double) step / steps;
            return (1 - fraction) * startAngle + fraction * endAngle;
        }
    }
}
