package com.modularmc.mmclutils.client.render.effect;

import org.jspecify.annotations.Nullable;

/**
 * 在客户端提交带阻力和重力的光点，或提交向指定位置移动的光点。
 * 在客户端渲染线程调用。粒子工厂可以传入自己的精灵，由批量管线统一计算和绘制。
 * 超出批次容量或没有客户端世界时跳过生成。
 */
public final class ParticleEffects {

    /** 光点的边长随寿命线性缩小。 */
    public static final int SHRINK = GpuParticleRenderer.SHRINK;
    /** 在寿命中点达到最大边长，两端收缩到零。 */
    public static final int PULSE = GpuParticleRenderer.PULSE_SIZE;
    /** 整个寿命使用固定边长。 */
    public static final int CONSTANT = GpuParticleRenderer.CONSTANT;
    /** 使光点的亮度或不透明度随寿命线性衰减。 */
    public static final int FADE = 1;
    /** 跳过场景深度测试，可与 {@link #FADE} 按位组合。 */
    public static final int THROUGH_WALLS = 2;
    /** 原版粒子发射器的相机距离上限，单位为方块。 */
    public static final double SPAWN_DISTANCE = 64;

    private ParticleEffects() {}

    /**
     * 发射受阻力和重力影响的光点。寿命、边长或亮度非正时直接返回。
     *
     * @param x         起点世界坐标 X
     * @param y         起点世界坐标 Y
     * @param z         起点世界坐标 Z
     * @param vx        X 轴初速度，单位为方块每 Tick
     * @param vy        Y 轴初速度，单位为方块每 Tick
     * @param vz        Z 轴初速度，单位为方块每 Tick
     * @param drag      每 Tick 的速度保留比例，范围为 0～1
     * @param gravity   竖直向下加速度，单位为方块每 Tick 的平方
     * @param life      寿命，单位为 Tick
     * @param size      基础边长，单位为方块
     * @param sizeCurve {@link #SHRINK}、{@link #PULSE} 或 {@link #CONSTANT}
     * @param rgb       打包在低 24 位的 RGB
     * @param alpha     亮度或不透明度，范围为 0～1
     * @param flags     由 {@link #FADE} 与 {@link #THROUGH_WALLS} 组合的位掩码
     * @param look      光点外观
     */
    public static void spawn(double x, double y, double z, double vx, double vy, double vz, float drag, float gravity, float life, float size, int sizeCurve,
                             int rgb, float alpha, int flags, MoteLook look) {
        if (life <= 0 || size <= 0 || alpha <= 0) return;
        double now = EffectRenderer.now();
        if (Double.isNaN(now)) return;
        GpuParticleRenderer.write(GpuParticleRenderer.BALLISTIC, x, y, z, vx, vy, vz, now, life, drag, gravity, size, alpha, rgb, sizeCurve, flags & FADE, 0, look.rollSpeed(), 0, 0,
                0, 0, look, (flags & THROUGH_WALLS) != 0);
    }

    /**
     * 生成从起点匀速滑向终点的光点，滑行时间等于寿命。
     *
     * @param x0        起点世界坐标 X
     * @param y0        起点世界坐标 Y
     * @param z0        起点世界坐标 Z
     * @param x1        终点世界坐标 X
     * @param y1        终点世界坐标 Y
     * @param z1        终点世界坐标 Z
     * @param life      寿命，单位为 Tick
     * @param size      基础边长，单位为方块
     * @param sizeCurve {@link #SHRINK}、{@link #PULSE} 或 {@link #CONSTANT}
     * @param rgb       打包在低 24 位的 RGB
     * @param alpha     亮度或不透明度，范围为 0～1
     * @param flags     由 {@link #FADE} 与 {@link #THROUGH_WALLS} 组合的位掩码
     * @param look      光点外观
     */
    public static void slide(double x0, double y0, double z0, double x1, double y1, double z1, float life, float size, int sizeCurve, int rgb, float alpha,
                             int flags, MoteLook look) {
        if (life <= 0 || size <= 0 || alpha <= 0) return;
        double now = EffectRenderer.now();
        if (Double.isNaN(now)) return;
        GpuParticleRenderer.write(GpuParticleRenderer.SLIDE, x0, y0, z0, x1 - x0, y1 - y0, z1 - z0, now, life, 1, 0, size, alpha, rgb, sizeCurve, flags & FADE, 0, look.rollSpeed(),
                0, 0, 0, 0, look, (flags & THROUGH_WALLS) != 0);
    }

    /**
     * 光点样式可选择带亮芯的像素光晕，或由调用方提供的粒子精灵。
     *
     * @param sprite    粒子图集精灵，空值表示使用内置光晕
     * @param animated  是否在寿命内顺序播放各帧
     * @param rollSpeed 绕朝向相机的轴旋转的速度，单位为弧度每 Tick
     * @param core      内置光晕亮芯相对光点的边长倍率，非正值时省略亮芯
     */
    public record MoteLook(@Nullable MoteSprite sprite, boolean animated, float rollSpeed, float core) {

        /** 默认光晕样式，亮芯边长为光点边长的 30%。 */
        public static final MoteLook GLOW = new MoteLook(null, false, 0, 0.3F);

        /** 使用内置光晕与给定亮芯边长倍率。 */
        public static MoteLook glow(float core) {
            return new MoteLook(null, false, 0, core);
        }

        /** 从精灵集合选定一帧，在光点寿命内保留该帧。 */
        public static MoteLook sprite(MoteSprite sprite) {
            return new MoteLook(sprite, false, 0, 0);
        }

        /** 在光点寿命内顺序播放精灵帧。 */
        public static MoteLook animated(MoteSprite sprite) {
            return new MoteLook(sprite, true, 0, 0);
        }

        /** 从精灵集合选定一帧并按指定角速度旋转，单位为弧度每 Tick。 */
        public static MoteLook spinning(MoteSprite sprite, float rollSpeed) {
            return new MoteLook(sprite, false, rollSpeed, 0);
        }
    }
}
