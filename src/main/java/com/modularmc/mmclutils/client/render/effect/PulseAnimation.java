package com.modularmc.mmclutils.client.render.effect;

import net.minecraft.world.phys.Vec3;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.Nullable;

/** 从固定起点向外推进的周期脉冲。所有时间参数使用同一种时间单位。 */
public final class PulseAnimation {

    private final double period;
    private final double speed;
    private final double maxRadius;
    @Getter
    @Accessors(fluent = true)
    private @Nullable Vec3 origin;
    private double startedAt;
    private double lastTime;
    @Getter
    @Accessors(fluent = true)
    private double radius;

    public PulseAnimation(double period, double speed, double maxRadius) {
        if (!(Double.isFinite(period) && period > 0 && Double.isFinite(speed) && speed > 0 && Double.isFinite(maxRadius) && maxRadius > 0)) {
            throw new IllegalArgumentException("脉冲周期、速度和半径必须为有限正数");
        }
        this.period = period;
        this.speed = speed;
        this.maxRadius = maxRadius;
    }

    /** 在当前时间更新脉冲。若开始新一轮，记录此刻的位置并返回真。 */
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

    public double radiusSquared() {
        return radius * radius;
    }

    public void clear() {
        origin = null;
        radius = 0;
    }
}
