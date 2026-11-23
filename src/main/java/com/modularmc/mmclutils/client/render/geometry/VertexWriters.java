package com.modularmc.mmclutils.client.render.geometry;

import com.modularmc.mmclutils.client.render.quad.QuadGeometry;
import com.modularmc.mmclutils.client.render.quad.QuadView;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.IntUnaryOperator;

/** 按原版实体顶点格式提交位置、调制色、图集 UV、光照和法线。 */
public final class VertexWriters {

    private VertexWriters() {}

    public static void vertex(VertexConsumer vertices, PoseStack.Pose pose, Vector3fc position, int color,
                              float u, float v, int overlay, int light, Vector3fc normal) {
        vertices.addVertex(pose, position).setColor(color).setUv(u, v).setOverlay(overlay).setLight(light).setNormal(pose, normal);
    }

    public static void quad(VertexConsumer vertices, PoseStack.Pose pose, QuadView quad, int overlay, int light) {
        quad(vertices, pose, quad, overlay, ignored -> light);
    }

    public static void quad(VertexConsumer vertices, PoseStack.Pose pose, QuadView quad, int overlay, IntUnaryOperator light) {
        var normal = new Vector3f();
        for (int index = 0; index < QuadView.VERTEX_COUNT; index++) {
            vertex(vertices, pose, quad.position(index), quad.color(index), quad.u(index), quad.v(index), overlay,
                    light.applyAsInt(index), QuadGeometry.vertexNormal(quad, index, normal));
        }
    }
}
