package com.modularmc.mmclutils.client.render.effect;

import net.minecraft.world.phys.Vec3;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.Nullable;

/** 从固定起点向外推进的周期脉冲。由同一线程更新和读取，所有时间参数使用同一种单位。 */
public final class PulseAnimation {

    private final double period;
    private final double speed;
    private final double maxRadius;
    /** 本轮脉冲起点，清空后或首次更新前为空值。 */
    @Getter
    @Accessors(fluent = true)
    private @Nullable Vec3 origin;
    private double startedAt;
    private double lastTime;
    /** 当前半径，单位为方块。 */
    @Getter
    @Accessors(fluent = true)
    private double radius;

    /**
     * 创建周期脉冲，届期后在下一次更新的位置开始新一轮。
     *
     * @param period    两次发射之间的时间
     * @param speed     每单位时间扩张的方块数
     * @param maxRadius 最大半径，单位为方块
     * @throws IllegalArgumentException 有参数为非正数或非有限值
     */
    public PulseAnimation(double period, double speed, double maxRadius) {
        if (!(Double.isFinite(period) && period > 0 && Double.isFinite(speed) && speed > 0 && Double.isFinite(maxRadius) && maxRadius > 0)) {
            throw new IllegalArgumentException("脉冲周期、速度和半径必须为有限正数");
        }
        this.period = period;
        this.speed = speed;
        this.maxRadius = maxRadius;
    }

    /**
     * 在当前时间更新脉冲。首次调用、周期届满或时间倒退时重新发射。
     *
     * @param time   当前时间，须为有限值
     * @param center 重新发射时采样的世界坐标
     * @return 是否开始新的一轮
     * @throws IllegalArgumentException 时间为非有限值
     */
    public boolean update(double time, Vec3 center) {
        if (!Double.isFinite(time)) throw new IllegalArgumentException("脉冲时间须为有限值");
        boolean restarted = origin == null || time < lastTime || time - startedAt >= period;
        if (restarted) {
            startedAt = time;
            origin = center;
        }
        lastTime = time;
        radius = Math.min((time - startedAt) * speed, maxRadius);
        return restarted;
    }

    /** 返回当前半径的平方，供距离平方比较。 */
    public double radiusSquared() {
        return radius * radius;
    }

    /** 清空起点与半径，使下一次更新重新发射。 */
    public void clear() {
        origin = null;
        radius = 0;
    }
}
