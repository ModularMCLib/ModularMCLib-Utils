package com.modularmc.mmclutils.client.render.effect;

import com.modularmc.mmclutils.client.render.effect.BurstEffects.BurstStyle;
import com.modularmc.mmclutils.client.render.effect.ParticleEffects.MoteLook;

import net.minecraft.util.Mth;

import org.jspecify.annotations.Nullable;

import java.util.SplittableRandom;

/** 在客户端渲染线程提交端点之间的定点连线、循环粒子流和传播脉冲。 */
public final class BeamEffects {

    private static final SplittableRandom RANDOM = new SplittableRandom(0x2545F4914F6CDD1DL);

    private BeamEffects() {}

    static float unit(long seed, int salt) {
        long z = seed + 0x9E3779B97F4A7C15L * (salt + 1);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 40) * 0x1.0p-24F;
    }

    /**
     * 为当帧创建沿线循环的光点流，应在 {@link EffectRenderer.Source#collect} 中每帧提交。
     *
     * @param x0    起点世界坐标 X
     * @param y0    起点世界坐标 Y
     * @param z0    起点世界坐标 Z
     * @param x1    终点世界坐标 X
     * @param y1    终点世界坐标 Y
     * @param z1    终点世界坐标 Z
     * @param rgb   光点颜色的低 24 位 RGB
     * @param seed  固定同一束流的发射相位和扰动
     * @param style 发射周期、行进时间和扰动参数
     * @param look  光点外观
     */
    public static void flow(double x0, double y0, double z0, double x1, double y1, double z1, int rgb, long seed, FlowStyle style, MoteLook look) {
        if (!EffectRenderer.open()) return;
        GpuParticleRenderer.rel(EffectRenderer.frameTime());
        int period = Math.max(1, style.period());
        int travel = Math.max(1, style.travel());
        int lanes = (travel + period - 1) / period + 1;
        float phase = unit(seed, 0) * period;
        float hashSeed = unit(seed, 1) * 1000;
        for (int k = 0; k < lanes; k++) {
            GpuParticleRenderer.write(GpuParticleRenderer.FLOW, x0, y0, z0, x1 - x0, y1 - y0, z1 - z0, 0, 0, style.sizeJitter(), style.jitter(), style.size(), style.alpha(), rgb,
                    GpuParticleRenderer.CONSTANT, 0, hashSeed, 0, period, travel, phase, k, look, false);
        }
    }

    static double pulseDuration(double length, PulseStyle style) {
        double v = Math.max(0.01, style.speed()), lag = Math.max(0.01, style.lag());
        double t = length / v + lag;
        for (int i = 0; i < 6; i++) {
            double e = Math.exp(-t / lag);
            double s = v * (t - lag * (1 - e)) - length;
            double ds = v * (1 - e);
            if (ds <= 1e-6) break;
            t -= s / ds;
        }
        return Math.max(1, t);
    }

    /**
     * 向终点发送一次脉冲，并按样式生成延迟的尾迹和到达爆散。
     *
     * @param x0    起点世界坐标 X
     * @param y0    起点世界坐标 Y
     * @param z0    起点世界坐标 Z
     * @param x1    终点世界坐标 X
     * @param y1    终点世界坐标 Y
     * @param z1    终点世界坐标 Z
     * @param rgb   光点颜色的低 24 位 RGB
     * @param style 传播和尾迹参数
     * @param look  光点外观
     */
    public static void pulse(double x0, double y0, double z0, double x1, double y1, double z1, int rgb, PulseStyle style, MoteLook look) {
        double now = EffectRenderer.now();
        if (Double.isNaN(now)) return;
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float duration = (float) pulseDuration(length, style);
        float lag = Math.max(0.01F, style.lag());
        GpuParticleRenderer.write(GpuParticleRenderer.PULSE, x0, y0, z0, dx, dy, dz, now, duration, style.speed(), lag, style.size(), style.alpha(), rgb, GpuParticleRenderer.CONSTANT, 0, 0,
                look.rollSpeed(), 0, 1, length, duration, look, false);
        float trailLife = Math.max(0.5F, style.trailLife());
        float w = style.size() * 0.45F;
        for (int i = 1; i <= style.trail(); i++) {
            float delay = i * style.trailStep();
            float a = 1 - delay / trailLife;
            if (a <= 0) break;
            double ox = (RANDOM.nextFloat() - 0.5F) * w, oy = (RANDOM.nextFloat() - 0.5F) * w, oz = (RANDOM.nextFloat() - 0.5F) * w;
            GpuParticleRenderer.write(GpuParticleRenderer.PULSE, x0 + ox, y0 + oy, z0 + oz, dx, dy, dz, now, duration + delay, style.speed(), lag, style.size(), style.alpha() * a, rgb,
                    GpuParticleRenderer.CONSTANT, 0, 0, look.rollSpeed(), delay, 0.7F + 0.3F * a, length, duration, look, false);
        }
        var arrival = style.arrival();
        if (arrival != null) BurstEffects.burstAt(x1, y1, z1, rgb, arrival, look, now + duration);
    }

    /**
     * 沿线段一次性生成一排短寿命的静止光点。两端重合或没有客户端世界时直接返回。
     *
     * @param x0    起点世界坐标 X
     * @param y0    起点世界坐标 Y
     * @param z0    起点世界坐标 Z
     * @param x1    终点世界坐标 X
     * @param y1    终点世界坐标 Y
     * @param z1    终点世界坐标 Z
     * @param rgb   使用固定颜色时的低 24 位 RGB
     * @param style 间距、寿命和颜色参数
     * @param look  光点外观
     */
    public static void beam(double x0, double y0, double z0, double x1, double y1, double z1, int rgb, BeamStyle style, MoteLook look) {
        double now = EffectRenderer.now();
        if (Double.isNaN(now)) return;
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 1e-4) return;
        float step = Math.max(0.05F, style.spacing());
        int n = Math.min(512, (int) (length / step));
        float hue = RANDOM.nextFloat();
        for (int i = 0; i <= n; i++) {
            double d = (i + (RANDOM.nextFloat() - 0.5F) * style.spacingJitter()) * step / length;
            if (d < 0 || d > 1) continue;
            int color = rgb;
            if (style.rainbow()) {
                int c = Mth.hsvToRgb(Mth.frac(hue + (float) d), 1, 1);
                int r = Math.min(255, (c >> 16 & 0xFF) + 102), g = Math.min(255, (c >> 8 & 0xFF) + 102), b = Math.min(255, (c & 0xFF) + 102);
                color = r << 16 | g << 8 | b;
            }
            GpuParticleRenderer.write(GpuParticleRenderer.BALLISTIC, x0 + dx * d, y0 + dy * d, z0 + dz * d, 0, 0, 0, now, Math.max(1, style.life()), 1, 0, style.size(),
                    style.alpha(), color, look.animated() ? GpuParticleRenderer.SHRINK : GpuParticleRenderer.CONSTANT, look.animated() ? 0 : GpuParticleRenderer.FADE, 0, look.rollSpeed(), 0, 0, 0, 0, look, false);
        }
    }

    /**
     * 沿直线生成的粒子间距、寿命和着色参数。
     *
     * @param spacing       间距，单位为方块，有效值至少为 0.05
     * @param spacingJitter 单个光点沿连线的偏移范围，以间距为单位
     * @param life          寿命，单位为 Tick，有效值至少为 1
     * @param size          光点边长，单位为方块
     * @param alpha         光点不透明度或亮度，范围为 0～1
     * @param rainbow       是否沿连线使用虹彩
     */
    public record BeamStyle(float spacing, float spacingJitter, int life, float size, float alpha, boolean rainbow) {}

    /**
     * 端点间循环移动的粒子流样式。
     *
     * @param period     相邻两次发射的间隔，单位为 Tick
     * @param travel     光点从起点到终点耗时，单位为 Tick
     * @param size       基础边长，单位为方块
     * @param sizeJitter 边长随机增加的最大比例
     * @param jitter     光点在各轴上的随机偏移范围，单位为方块
     * @param alpha      不透明度或亮度，范围为 0～1
     */
    public record FlowStyle(int period, int travel, float size, float sizeJitter, float jitter, float alpha) {}

    /**
     * 沿线传播的脉冲参数。
     *
     * @param speed     巡航速度，单位为方块每 Tick，应为正数
     * @param lag       从起点加速的时间常数，单位为 Tick
     * @param trail     尾迹粒子数量上限
     * @param trailStep 相邻尾迹粒子的延时，单位为 Tick
     * @param trailLife 用于计算尾迹衰减的寿命，单位为 Tick
     * @param size      光点边长，单位为方块
     * @param alpha     不透明度或亮度，范围为 0～1
     * @param arrival   到达后使用的爆散样式，空值时省略爆散
     */
    public record PulseStyle(float speed, float lag, int trail, float trailStep, float trailLife, float size, float alpha, @Nullable BurstStyle arrival) {}
}
