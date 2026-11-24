package com.modularmc.mmclutils.api.schedule;

import java.util.function.DoubleSupplier;

/**
 * 在一个以 Tick 配置的周期内提供从零到一的连续进度值。
 * 实例由调用方在单一线程访问，时间基于系统时钟而非游戏 Tick。
 */
public class ProgressTimer implements DoubleSupplier {

    private final long msPerCycle;
    private final int maxValue;
    private final boolean countDown;
    private long startTime;

    /**
     * 创建指定周期长度和进度范围的计时器。
     *
     * @param ticksPerCycle 一个周期包含的 Tick 数，必须为正数
     * @param maxValue 归一化前使用的正整数范围
     * @param countDown 为 {@code true} 时进度从一递减到零
     * @throws IllegalArgumentException 周期长度或进度范围不是正数
     */
    public ProgressTimer(int ticksPerCycle, int maxValue, boolean countDown) {
        if (ticksPerCycle <= 0 || maxValue <= 0) throw new IllegalArgumentException("计时周期和进度范围须为正数");
        this.msPerCycle = (long) ticksPerCycle * 50;
        this.maxValue = maxValue;
        this.countDown = countDown;
        this.startTime = System.currentTimeMillis();
    }

    /** 重置当前周期的起始时间，重新开始计时或倒计时。 */
    public void resetCountdown() {
        startTime = System.currentTimeMillis();
    }

    /** 返回当前周期内归一化到零至一的进度。 */
    @Override
    public double getAsDouble() {
        return calculateTime();
    }

    private double calculateTime() {
        long currentTime = System.currentTimeMillis();
        long msPassed = (currentTime - startTime) % msPerCycle;
        double currentValue = (1.0 * msPassed * maxValue) / msPerCycle;
        if (countDown) {
            return (maxValue - currentValue) / maxValue;
        }
        return currentValue / maxValue;
    }
}
