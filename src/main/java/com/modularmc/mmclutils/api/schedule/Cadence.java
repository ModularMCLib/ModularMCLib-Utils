package com.modularmc.mmclutils.api.schedule;

/**
 * 以 Tick 为单位的周期与相位设置，供 {@link TickScheduler} 创建周期任务。
 * 自动相位会轮流分配任务在各周期中的执行 Tick。
 */
public final class Cadence {

    private static final int AUTO = -1;
    private static final Cadence[] CACHED = new Cadence[129];

    static {
        for (int p = 1; p < CACHED.length; p++) CACHED[p] = new Cadence(p, AUTO);
    }

    /** 每个 Tick 执行一次的周期设置。 */
    public static final Cadence EVERY_TICK = CACHED[1];

    private final int period;
    private final int phase;

    private Cadence(int period, int phase) {
        this.period = period;
        this.phase = phase;
    }

    /**
     * 创建指定周期并采用自动相位的设置。周期小于一时按一处理。
     *
     * @param period 周期 Tick 数
     * @return 可供周期任务使用的设置
     */
    public static Cadence every(int period) {
        int p = Math.max(1, period);
        return p < CACHED.length ? CACHED[p] : new Cadence(p, AUTO);
    }

    /**
     * 返回使用指定相位的设置，相位会规范化到 {@code [0, period)}。
     *
     * @param phase 周期内的执行相位
     * @return 具有规范化固定相位的新设置
     */
    public Cadence phase(int phase) {
        return new Cadence(period, Math.floorMod(phase, period));
    }

    /** 返回每次执行之间的 Tick 数，至少为一。 */
    public int period() {
        return period;
    }

    /** 判断此设置是否让调度器自动分配周期相位。 */
    public boolean isAutoPhase() {
        return phase == AUTO;
    }

    /**
     * 返回固定相位，自动相位时返回内部标记值。
     *
     * @return 固定相位或自动相位标记
     */
    public int phase() {
        return phase;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Cadence c && c.period == period && c.phase == phase;
    }

    @Override
    public int hashCode() {
        return period * 31 + phase;
    }

    @Override
    public String toString() {
        return phase == AUTO ? "every " + period : "every " + period + " at " + phase;
    }
}
