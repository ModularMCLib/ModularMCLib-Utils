package com.modularmc.mmclutils.client.render.effect;

import net.minecraft.client.Camera;
import net.minecraft.core.Direction;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.util.Mth;

import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.List;

/** 在普通顶点路径上按环着色器的规则构造实体、精灵和光带。 */
final class RingGeometry {

    private static final int[] CORNER_X = { -1, 1, 1, -1 };
    private static final int[] CORNER_Y = { -1, -1, 1, 1 };
    private static final Vector3f LEFT = new Vector3f();
    private static final Vector3f UP = new Vector3f();
    private static final Vector3f CENTER = new Vector3f();
    private static final Matrix3f ROTATION = new Matrix3f();
    private static final Vector3f NORMAL = new Vector3f();
    private static final Vector3f TANGENT = new Vector3f();
    private static final Vector3f BITANGENT = new Vector3f();
    private static final Vector3f A = new Vector3f();
    private static final Vector3f B = new Vector3f();
    private static final Vector3f SIDE_A = new Vector3f();
    private static final Vector3f SIDE_B = new Vector3f();
    private static final EffectVertices VOXELS = new EffectVertices();
    private static final EffectVertices GLOWS = new EffectVertices();

    private RingGeometry() {}

    static void draw(List<EffectRenderer.RingCommand> commands, int count, Camera camera, EffectQuadBuffer voxels, EffectQuadBuffer glows) {
        LEFT.set(-1, 0, 0).rotate(camera.rotation());
        UP.set(0, 1, 0).rotate(camera.rotation());
        int particles = 0;
        for (int i = 0; i < count; i++) particles = Math.addExact(particles, commands.get(i).ring.count());
        VOXELS.begin(Math.multiplyExact(particles, 3));
        GLOWS.begin(Math.addExact(particles, Math.multiplyExact(count, 2 * RingRenderer.RIBBON_SEGMENTS)));
        for (int i = 0; i < count; i++) {
            var command = commands.get(i);
            particles(command);
            float strength = command.intensity * EffectRenderer.breathe(command);
            ribbon(command, command.style.hazeWidth(), (int) (command.style.hazeStrength() * strength));
            ribbon(command, command.style.coreWidth(), (int) (command.style.coreStrength() * strength));
        }
        VOXELS.upload(voxels);
        GLOWS.upload(glows);
        voxels.draw(EffectRenderPipelines.VOXEL, EffectRenderPipelines.WHITE_TEXTURE);
        glows.draw(EffectRenderPipelines.GLOW, EffectRenderPipelines.GLOW_TEXTURE);
    }

    private static void particles(EffectRenderer.RingCommand command) {
        ParticleRing ring = command.ring;
        ParticleRing.RingShape shape = command.shape;
        int strands = shape.strands();
        float radius = ring.majorRadius();
        float length = (float) Math.hypot(shape.turns() * Mth.TWO_PI * radius, shape.height());
        float time = (float) command.time;
        float half = ring.half() * command.scale;
        for (int i = 0; i < ring.count(); i++) {
            float phase = ring.phase(i);
            float orbit = ring.orbit(i);
            float radial = ring.radial(i, time);
            float height = ring.vertical(i, time);
            float angle = phase + orbit * time;
            float fade = 1;
            if (strands != 0) {
                float slot = phase / Mth.TWO_PI * strands;
                int strand = Math.min(Mth.floor(slot), strands - 1);
                float sweep = Mth.frac(slot - strand + orbit * radius / length * time);
                float along = helixAlong(shape, sweep);
                angle = Mth.TWO_PI * (strand / (float) strands + shape.turns() * sweep);
                height += along * shape.height();
                fade = helixFade(shape, along);
            }
            if (fade == 0) continue;
            point(command, angle, radial, height, CENTER);
            int rgb = command.palette[ring.tone(i)];
            float red = ARGB32.red(rgb) / 255f, green = ARGB32.green(rgb) / 255f, blue = ARGB32.blue(rgb) / 255f;
            if (half > 0) {
                ring.spin(i, time, ROTATION);
                voxel(half * fade, red, green, blue);
            }
            float brightness = command.spriteStrength * fade / 255;
            if (brightness > 0) {
                GLOWS.facing(CENTER.x, CENTER.y, CENTER.z, LEFT, UP, 2 * command.spriteSize * fade, 0,
                        red * brightness, green * brightness, blue * brightness, 1, 0, 0, 1, 1);
            }
        }
    }

    private static void voxel(float half, float red, float green, float blue) {
        for (int face = 0; face < 3; face++) {
            ROTATION.getColumn(face, NORMAL);
            if (NORMAL.dot(CENTER) > 0) NORMAL.negate();
            ROTATION.getColumn((face + 1) % 3, TANGENT);
            NORMAL.cross(TANGENT, BITANGENT);
            float shade = 0.75f + 0.25f * NORMAL.y + 0.05f * (Math.abs(NORMAL.z) - Math.abs(NORMAL.x));
            for (int corner = 0; corner < 4; corner++) {
                float x = CORNER_X[corner], y = CORNER_Y[corner];
                VOXELS.writeVertex(
                        CENTER.x + (NORMAL.x + x * TANGENT.x + y * BITANGENT.x) * half,
                        CENTER.y + (NORMAL.y + x * TANGENT.y + y * BITANGENT.y) * half,
                        CENTER.z + (NORMAL.z + x * TANGENT.z + y * BITANGENT.z) * half,
                        red * shade, green * shade, blue * shade, 1, 0.5f, 0.5f);
            }
        }
    }

    private static void ribbon(EffectRenderer.RingCommand command, float width, int strength) {
        if (width <= 0 || strength <= 0) return;
        float red = ARGB32.red(command.baseColor) * strength / 65025f;
        float green = ARGB32.green(command.baseColor) * strength / 65025f;
        float blue = ARGB32.blue(command.baseColor) * strength / 65025f;
        int strands = Math.max(1, command.shape.strands());
        for (int strand = 0; strand < strands; strand++) {
            int pieces = RingRenderer.RIBBON_SEGMENTS / strands + (strand < RingRenderer.RIBBON_SEGMENTS % strands ? 1 : 0);
            ribbonSample(command, strand, 0, width, A, SIDE_A);
            for (int piece = 1; piece <= pieces; piece++) {
                ribbonSample(command, strand, piece / (float) pieces, width, B, SIDE_B);
                ribbonVertex(A, SIDE_A, 1, red, green, blue, 0);
                ribbonVertex(A, SIDE_A, -1, red, green, blue, 1);
                ribbonVertex(B, SIDE_B, -1, red, green, blue, 1);
                ribbonVertex(B, SIDE_B, 1, red, green, blue, 0);
                A.set(B);
                SIDE_A.set(SIDE_B);
            }
        }
    }

    private static void ribbonSample(EffectRenderer.RingCommand command, int strand, float sweep, float width, Vector3f point, Vector3f side) {
        ParticleRing.RingShape shape = command.shape;
        float radius = command.ring.majorRadius();
        if (shape.strands() == 0) {
            float angle = Mth.TWO_PI * sweep;
            point(command, angle, radius, 0, point);
            orient(command.axis, -(float) Math.sin(angle), 0, (float) Math.cos(angle), side);
        } else {
            float along = helixAlong(shape, sweep);
            float offset = strand / (float) shape.strands();
            point(command, Mth.TWO_PI * (offset + shape.turns() * sweep), radius, along * shape.height(), point);
            float ahead = sweep + 0.001f;
            point(command, Mth.TWO_PI * (offset + shape.turns() * ahead), radius, helixAlong(shape, ahead) * shape.height(), side);
            side.sub(point);
            width *= helixFade(shape, along);
        }
        side.cross(point).mul(width / Math.max(side.length(), 0.0001f));
    }

    private static void ribbonVertex(Vector3f point, Vector3f side, float sign, float red, float green, float blue, float u) {
        GLOWS.writeVertex(point.x + sign * side.x, point.y + sign * side.y, point.z + sign * side.z, red, green, blue, 1, u, 0.5f);
    }

    private static void point(EffectRenderer.RingCommand command, float angle, float radius, float height, Vector3f output) {
        orient(command.axis, (float) Math.cos(angle) * radius, height, (float) Math.sin(angle) * radius, output);
        output.add(command.x, command.y, command.z);
    }

    private static void orient(Direction.Axis axis, float x, float y, float z, Vector3f output) {
        switch (axis) {
            case X -> output.set(y, x, z);
            case Y -> output.set(x, y, z);
            case Z -> output.set(x, z, y);
        }
    }

    private static float helixAlong(ParticleRing.RingShape shape, float sweep) {
        float taper = shape.taper();
        float lead = 1 + taper;
        return 2 * sweep / (lead + Mth.sqrt(Math.max(0, lead * lead - 4 * taper * sweep)));
    }

    private static float helixFade(ParticleRing.RingShape shape, float along) {
        double start = Mth.clamp(along / Math.max(shape.fadeIn(), 0.0001f), 0, 1);
        double end = Mth.clamp((1 - along) / Math.max(shape.fadeOut(), 0.0001f), 0, 1);
        return (float) (Mth.smoothstep(start) * Mth.smoothstep(end));
    }

    static void release() {
        VOXELS.release();
        GLOWS.release();
    }
}
