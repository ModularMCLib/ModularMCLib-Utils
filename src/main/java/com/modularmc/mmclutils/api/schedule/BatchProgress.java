package com.modularmc.mmclutils.api.schedule;

/**
 * 计算状态维持不变的一段推进量，并在本次完成边界处停下。
 */
public final class BatchProgress {

    private BatchProgress() {}

    /**
     * 推进到可用 Tick 用完或本轮完成。
     *
     * @param progress       当前进度，范围为 {@code [0, duration)}
     * @param duration       完成一次所需的正数 Tick 数
     * @param availableTicks 本批次可用的正数 Tick 数
     * @return 消耗的 Tick 数、推进后的进度及是否到达完成边界
     */
    public static Segment advanceToBoundary(int progress, int duration, int availableTicks) {
        if (duration <= 0) {
            throw new IllegalArgumentException("duration must be positive");
        }
        if (progress < 0 || progress >= duration) {
            throw new IllegalArgumentException("progress must be within [0, duration)");
        }
        if (availableTicks <= 0) {
            throw new IllegalArgumentException("availableTicks must be positive");
        }

        int ticksToBoundary = duration - progress;
        if (availableTicks < ticksToBoundary) {
            return new Segment(availableTicks, progress + availableTicks, false);
        }
        return new Segment(ticksToBoundary, 0, true);
    }

    /**
     * 一次状态连续的推进结果。
     *
     * @param elapsedTicks    本次消耗的 Tick 数
     * @param progress        推进后的进度，到达完成边界时归零
     * @param reachedBoundary 是否完成了当前周期
     */
    public record Segment(int elapsedTicks, int progress, boolean reachedBoundary) {}
}
