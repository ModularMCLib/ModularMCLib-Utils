package com.modularmc.mmclutils.client.render.geometry;

import com.modularmc.mmclutils.client.model.QuadUtils;
import com.modularmc.mmclutils.client.model.QuadView;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.IntUnaryOperator;

/** 按原版实体顶点格式提交位置、调制色、图集 UV、光照和法线。 */
public final class VertexWriters {

    private VertexWriters() {}

    /** 按指定姿态写出位置和法线，其余属性使用传入的图集 UV、ARGB、覆盖层与打包光照。 */
    public static void vertex(VertexConsumer vertices, PoseStack.Pose pose, Vector3fc position, int color,
                              float u, float v, int overlay, int light, Vector3fc normal) {
        vertices.addVertex(pose, position.x(), position.y(), position.z()).setColor(color).setUv(u, v)
                .setOverlay(overlay).setLight(light).setNormal(pose, normal.x(), normal.y(), normal.z());
    }

    /** 按四边形顶点顺序写入，同一打包光照应用于四点。 */
    public static void quad(VertexConsumer vertices, PoseStack.Pose pose, QuadView quad, int overlay, int light) {
        quad(vertices, pose, quad, overlay, ignored -> light);
    }

    /** 按四边形顶点顺序写入。回调按顶点索引分别提供打包光照。 */
    public static void quad(VertexConsumer vertices, PoseStack.Pose pose, QuadView quad, int overlay, IntUnaryOperator light) {
        var normal = new Vector3f();
        for (int index = 0; index < QuadView.VERTEX_COUNT; index++) {
            vertex(vertices, pose, quad.position(index), quad.color(index), quad.u(index), quad.v(index), overlay,
                    light.applyAsInt(index), QuadUtils.vertexNormal(quad, index, normal));
        }
    }
}
