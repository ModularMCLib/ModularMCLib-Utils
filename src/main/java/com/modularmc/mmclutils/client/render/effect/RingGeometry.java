package com.modularmc.mmclutils.client.render.effect;

import net.minecraft.client.Camera;
import net.minecraft.core.Direction;

import org.joml.Vector3f;

import java.util.List;

/** 按环的数据和样式生成无变换反馈时使用的顶点。 */
final class RingGeometry {

    private static final Vector3f LEFT = new Vector3f();
    private static final Vector3f UP = new Vector3f();
    private static final EffectVertices VERTICES = new EffectVertices();

    private RingGeometry() {}

    static void draw(List<EffectRenderer.RingCommand> commands, int count, Camera camera, EffectQuadBuffer output) {
        LEFT.set(-1, 0, 0).rotate(camera.rotation());
        UP.set(0, 1, 0).rotate(camera.rotation());
        for (int i = 0; i < count; i++) {
            var command = commands.get(i);
            VERTICES.begin(command.ring.count());
            for (int p = 0; p < command.ring.count(); p++) {
                double phase = command.ring.phase(p, command.time);
                double angle = phase;
                float height = command.ring.vertical(p, command.time);
                var shape = command.shape;
                if (shape.strands() > 0) {
                    double fraction = phase / (2 * Math.PI) - Math.floor(phase / (2 * Math.PI));
                    angle = 2 * Math.PI * (fraction * shape.turns() + (p % shape.strands()) / (double) shape.strands());
                    height += (float) ((fraction - .5) * shape.height());
                }
                float radius = command.ring.radial(p, command.time);
                float x = (float) (Math.cos(angle) * radius);
                float z = (float) (Math.sin(angle) * radius);
                float y = height;
                Direction.Axis axis = command.axis;
                if (axis == Direction.Axis.X) {
                    float previous = x;
                    x = y;
                    y = -previous;
                } else if (axis == Direction.Axis.Z) {
                    float previous = z;
                    z = y;
                    y = -previous;
                }
                int rgb = command.palette[command.ring.tone(p)];
                float alpha = Math.min(1f, command.spriteStrength / 255f);
                VERTICES.facing(command.x + x, command.y + y, command.z + z, LEFT, UP, command.spriteSize, 0,
                        (rgb >> 16 & 255) / 255f * alpha, (rgb >> 8 & 255) / 255f * alpha, (rgb & 255) / 255f * alpha,
                        alpha, 0, 0, 1, 1);
            }
            VERTICES.upload(output);
            output.draw(EffectRenderPipelines.GLOW, EffectRenderPipelines.GLOW_TEXTURE);
        }
    }

    static void release() {
        VERTICES.release();
    }
}
