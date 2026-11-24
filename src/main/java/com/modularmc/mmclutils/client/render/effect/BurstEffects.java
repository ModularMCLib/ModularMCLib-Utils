package com.modularmc.mmclutils.client.render.effect;

import com.modularmc.mmclutils.client.render.effect.ParticleEffects.MoteLook;

import net.minecraft.util.Mth;

import java.util.SplittableRandom;

/** 在客户端渲染线程提交由光点组成的一次性球形爆散。 */
public final class BurstEffects {

    private static final SplittableRandom RANDOM = new SplittableRandom(0x5DEECE66DL);

    private BurstEffects() {}

    /**
     * 在给定世界坐标生成一组逐渐扩散的光点，当前没有客户端世界时直接返回。
     *
     * @param x     中心坐标 X
     * @param y     中心坐标 Y
     * @param z     中心坐标 Z
     * @param rgb   光点颜色的低 24 位 RGB
     * @param style 扩散范围和寿命等参数
     * @param look  光点外观
     */
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

    /**
     * 一次爆散的粒子数量、寿命、初始分布与视觉参数。
     *
     * @param count    生成的光点数
     * @param life     寿命，单位为 Tick，有效值至少为 1
     * @param radius   单个光点的最大径向位移，单位为方块
     * @param spreadXZ 起点在 X、Z 轴上各自的偏移范围，单位为方块
     * @param spreadY  起点在 Y 轴上的偏移范围，单位为方块
     * @param rise     附加的竖直速度，单位为方块每 Tick
     * @param size     光点边长，单位为方块
     * @param alpha    不透明度或亮度，范围为 0～1
     */
    public record BurstStyle(int count, int life, float radius, float spreadXZ, float spreadY, float rise, float size, float alpha) {}
}
