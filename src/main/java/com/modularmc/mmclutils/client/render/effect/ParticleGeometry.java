package com.modularmc.mmclutils.client.render.effect;

import net.minecraft.client.Camera;

import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/** 在 CPU 上计算与光点着色器一致的轨迹、大小和颜色。 */
final class ParticleGeometry {

    private static final int BALLISTIC = 0, SLIDE = 1, PULSE = 2, BURST = 3, FLOW = 4;
    private static final int FADE = 1, ANIMATED = 4;
    private static final Vector3f LEFT = new Vector3f();
    private static final Vector3f UP = new Vector3f();
    private static final EffectVertices HALOS = new EffectVertices();
    private static final EffectVertices CORES = new EffectVertices();

    private ParticleGeometry() {}

    static void release() {
        HALOS.release();
        CORES.release();
    }

    static void generate(GpuParticleRenderer.Pool pool, double now, double epoch, Camera camera, ByteBuffer frames, int frameColumns) {
        int size = pool.stream ? pool.count : pool.highWater;
        var eye = camera.getPosition();
        LEFT.set(-1, 0, 0).rotate(camera.rotation());
        UP.set(0, 1, 0).rotate(camera.rotation());
        HALOS.begin(size);
        CORES.begin(pool.atlas ? 0 : size);
        for (int i = 0; i < size; i++) {
            long base = pool.address + (long) i * 128;
            float life = value(base, 7);
            float age = (float) (now - epoch - value(base, 3));
            int mode = (int) value(base, 11);
            int flags = (int) value(base, 25);
            float fraction = age / life;
            float alpha = value(base, 15);
            float x = (float) (value(base, 0) + (value(base, 4) - eye.x));
            float y = (float) (value(base, 1) + (value(base, 5) - eye.y));
            float z = (float) (value(base, 2) + (value(base, 6) - eye.z));
            float vx = value(base, 8), vy = value(base, 9), vz = value(base, 10);
            float scale = 1, progress = fraction, rise = 0;
            if (mode == FLOW) {
                float time = (float) (now - epoch);
                float period = value(base, 28), travel = value(base, 29), phase = value(base, 30);
                float slot = (float) Math.floor((time + phase) / period) - value(base, 31);
                progress = (time - (slot * period - phase)) / travel;
                if (progress < 0 || progress >= 1) continue;
                float seed = value(base, 26) * 131 + slot * 0.6180339f;
                float jitter = value(base, 13);
                x += jitter * lerp(hash(seed + 1) - .5f, hash(seed + 4) - .5f, progress);
                y += jitter * lerp(hash(seed + 2) - .5f, hash(seed + 5) - .5f, progress);
                z += jitter * lerp(hash(seed + 3) - .5f, hash(seed + 6) - .5f, progress);
                scale += value(base, 12) * hash(seed + 7);
                alpha *= Math.min(1, Math.min(progress, 1 - progress) / .18f);
                fraction = .5f;
            } else {
                if (age < 0 || fraction >= 1) continue;
                switch (mode) {
                    case BALLISTIC -> {
                        float drag = value(base, 12);
                        double fall;
                        if (Math.abs(1 - drag) < .000001) {
                            progress = age;
                            fall = age * (age + 1) * .5;
                        } else {
                            progress = (float) ((1 - Math.pow(drag, age)) / (1 - drag));
                            fall = (age - drag * progress) / (1 - drag);
                        }
                        rise = (float) (-value(base, 13) * fall);
                    }
                    case PULSE -> {
                        float delay = value(base, 28), length = value(base, 30), duration = value(base, 31);
                        float time = age - delay;
                        if (time < 0 || time > duration) continue;
                        float lag = Math.max(.01f, value(base, 13));
                        float speed = value(base, 12);
                        progress = length > .0001f ? (float) Math.min(1, speed * (time - lag * (1 - Math.exp(-time / lag))) / length) : 0;
                        scale = value(base, 29);
                    }
                    case BURST -> {
                        progress = 1 - (1 - fraction) * (1 - fraction);
                        rise = value(base, 13) * age;
                        alpha *= Math.min(1, age / 1.5f);
                    }
                    case SLIDE -> {}
                    default -> throw new IllegalStateException("未知光点轨迹：" + mode);
                }
                if ((flags & FADE) != 0) alpha *= 1 - fraction;
            }
            x += vx * progress;
            y += vy * progress + rise;
            z += vz * progress;
            if (alpha <= .004f || x * x + y * y + z * z > GpuParticleRenderer.CULL * GpuParticleRenderer.CULL) continue;
            float curve = switch ((int) value(base, 24)) {
                case 0 -> 1 - fraction;
                case 1 -> 1 - Math.abs(2 * fraction - 1);
                default -> 1;
            };
            float width = value(base, 14) * scale * curve;
            float red = value(base, 16), green = value(base, 17), blue = value(base, 18);
            float angle = mode == FLOW ? 0 : value(base, 26) + value(base, 27) * age;
            float u0 = value(base, 20), v0 = value(base, 21), u1 = value(base, 22), v1 = value(base, 23);
            if ((flags & ANIMATED) != 0) {
                int frame = (int) Math.min(v0 - 1, age * (v0 - 1) / life);
                int offset = ((int) u0 * frameColumns + frame) * 16;
                u0 = frames.getFloat(offset);
                v0 = frames.getFloat(offset + 4);
                u1 = frames.getFloat(offset + 8);
                v1 = frames.getFloat(offset + 12);
            }
            float brightness = pool.atlas ? 1 : alpha;
            HALOS.facing(x, y, z, LEFT, UP, width, angle, red * brightness, green * brightness, blue * brightness, alpha, u0, v0, u1, v1);
            float core = value(base, 19);
            if (!pool.atlas && core > 0) {
                CORES.facing(x, y, z, LEFT, UP, width * core, angle, lerp(red, 1, .55f) * alpha,
                        lerp(green, 1, .55f) * alpha, lerp(blue, 1, .55f) * alpha, alpha, 0, 0, 1, 1);
            }
        }
        HALOS.upload(pool.out);
        CORES.upload(pool.coreOut);
    }

    private static float value(long base, int index) {
        return MemoryUtil.memGetFloat(base + (long) index * Float.BYTES);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float hash(float value) {
        double product = Math.sin(value) * 43758.5453123;
        return (float) (product - Math.floor(product));
    }
}
