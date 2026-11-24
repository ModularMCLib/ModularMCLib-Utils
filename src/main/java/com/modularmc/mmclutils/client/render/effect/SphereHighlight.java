package com.modularmc.mmclutils.client.render.effect;

import com.modularmc.mmclutils.client.ModularMCLibUtilsClient;
import com.modularmc.mmclutils.client.render.geometry.MeshPrimitives;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** 在当前客户端世界中绘制持续 200 个游戏 Tick 的球形高亮。 */
public final class SphereHighlight {

    /** 高亮寿命，单位为未暂停的客户端游戏 Tick。 */
    public static final int DURATION = 200;
    private static final int LATITUDES = 40;
    private static final int LONGITUDES = 50;
    private static final EffectQuadBuffer BUFFER = new EffectQuadBuffer();
    private static final Matrix4f TRANSFORM = new Matrix4f();

    private static long position;
    private static int radius;
    private static int remaining;

    private SphereHighlight() {}

    /** 在客户端线程切换高亮。已有高亮时清除它，否则从指定方块坐标开始。 */
    public static void toggle(BlockPos center, int radius) {
        toggle(center.asLong(), radius);
    }

    /** 与方块坐标重载相同，直接使用 {@link BlockPos#asLong()} 的编码。 */
    public static void toggle(long center, int radius) {
        if (remaining != 0) {
            clear();
        } else if (ModularMCLibUtilsClient.getLevel() != null) {
            if (radius < 1) throw new IllegalArgumentException("高亮半径至少为一");
            position = center;
            SphereHighlight.radius = radius;
            remaining = DURATION;
        }
    }

    static void tick() {
        if (remaining > 0) remaining--;
    }

    /** 在客户端线程立即取消当前高亮。 */
    public static void clear() {
        remaining = 0;
    }

    static void draw(Vec3 camera, Matrix4fc modelView) {
        if (remaining == 0) return;
        if (BUFFER.vertices == 0) {
            var vertices = new EffectVertices();
            vertices.begin(LATITUDES * LONGITUDES);
            try {
                MeshPrimitives.sphere(1, LATITUDES, LONGITUDES,
                        (x, y, z) -> vertices.writeVertex(x, y, z, 0x3333FF, 0.25f, 0, 0));
                vertices.upload(BUFFER);
            } finally {
                vertices.release();
            }
        }
        TRANSFORM.set(modelView).translate(
                (float) (BlockPos.getX(position) - camera.x),
                (float) (BlockPos.getY(position) - camera.y),
                (float) (BlockPos.getZ(position) - camera.z)).scale(radius);
        BUFFER.draw(EffectRenderPipelines.HIGHLIGHT, EffectRenderPipelines.WHITE_TEXTURE, TRANSFORM);
    }

    static void release() {
        BUFFER.close();
    }
}
