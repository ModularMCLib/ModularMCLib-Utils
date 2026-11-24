package com.modularmc.mmclutils.client.render.effect;

/**
 * 在客户端提交带阻力和重力的光点，或提交向指定位置移动的光点。
 * 粒子工厂可以传入自己的精灵，由批量管线统一计算和绘制。
 */
public final class ParticleEffects {

    public static final int SHRINK = GpuParticleRenderer.SHRINK, PULSE = GpuParticleRenderer.PULSE_SIZE, CONSTANT = GpuParticleRenderer.CONSTANT;
    public static final int FADE = 1, THROUGH_WALLS = 2;
    public static final double SPAWN_DISTANCE = 64;

    private ParticleEffects() {}

    public static void spawn(double x, double y, double z, double vx, double vy, double vz, float drag, float gravity, float life, float size, int sizeCurve,
                             int rgb, float alpha, int flags, MoteLook look) {
        if (life <= 0 || size <= 0 || alpha <= 0) return;
        double now = EffectRenderer.now();
        if (Double.isNaN(now)) return;
        GpuParticleRenderer.write(GpuParticleRenderer.BALLISTIC, x, y, z, vx, vy, vz, now, life, drag, gravity, size, alpha, rgb, sizeCurve, flags & FADE, 0, look.rollSpeed(), 0, 0,
                0, 0, look, (flags & THROUGH_WALLS) != 0);
    }

    public static void slide(double x0, double y0, double z0, double x1, double y1, double z1, float life, float size, int sizeCurve, int rgb, float alpha,
                             int flags, MoteLook look) {
        if (life <= 0 || size <= 0 || alpha <= 0) return;
        double now = EffectRenderer.now();
        if (Double.isNaN(now)) return;
        GpuParticleRenderer.write(GpuParticleRenderer.SLIDE, x0, y0, z0, x1 - x0, y1 - y0, z1 - z0, now, life, 1, 0, size, alpha, rgb, sizeCurve, flags & FADE, 0, look.rollSpeed(),
                0, 0, 0, 0, look, (flags & THROUGH_WALLS) != 0);
    }
}
