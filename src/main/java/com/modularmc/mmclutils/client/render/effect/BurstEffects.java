package com.modularmc.mmclutils.client.render.effect;

import com.modularmc.mmclutils.client.render.effect.ParticleEffects.MoteLook;

import net.minecraft.util.Mth;

import java.util.SplittableRandom;

public final class BurstEffects {

    private static final SplittableRandom RANDOM = new SplittableRandom(0x5DEECE66DL);

    private BurstEffects() {}

    public static void burst(double x, double y, double z, int rgb, BurstStyle style, MoteLook look) {
        double now = EffectRenderer.now();
        if (!Double.isNaN(now)) burstAt(x, y, z, rgb, style, look, now);
    }

    static void burstAt(double x, double y, double z, int rgb, BurstStyle style, MoteLook look, double start) {
        float life = Math.max(1, style.life());
        for (int i = 0; i < style.count(); i++) {
            float u = RANDOM.nextFloat() * 2 - 1;
            float t = RANDOM.nextFloat() * Mth.TWO_PI;
            float s = Mth.sqrt(1 - u * u);
            float rad = style.radius() * (0.4F + 0.6F * RANDOM.nextFloat());
            double px = x + (RANDOM.nextFloat() - 0.5F) * style.spreadXZ();
            double py = y + (RANDOM.nextFloat() - 0.5F) * style.spreadY();
            double pz = z + (RANDOM.nextFloat() - 0.5F) * style.spreadXZ();
            GpuParticleRenderer.write(GpuParticleRenderer.BURST, px, py, pz, s * Mth.cos(t) * rad, u * rad, s * Mth.sin(t) * rad, start, life, 0, style.rise(), style.size(),
                    style.alpha(), rgb, GpuParticleRenderer.CONSTANT, GpuParticleRenderer.FADE, RANDOM.nextFloat() * Mth.TWO_PI, look.rollSpeed(), 0, 0, 0, 0, look, false);
        }
    }

    /** 一次爆散的粒子数量、寿命、初始分布与视觉参数。 */
    public record BurstStyle(int count, int life, float radius, float spreadXZ, float spreadY, float rise, float size, float alpha) {}
}
