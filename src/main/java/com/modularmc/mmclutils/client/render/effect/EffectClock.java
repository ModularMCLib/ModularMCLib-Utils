package com.modularmc.mmclutils.client.render.effect;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 为客户端渲染状态推进渐入、渐出与本地动画时间。
 * 以弱引用缓存所有者。时钟和缓存均在客户端渲染线程访问。
 */
public final class EffectClock {

    private static final Map<Object, EffectClock> CLOCKS = new WeakHashMap<>();
    private static final double MAX_STEP = 20;

    private final float riseTicks;
    private final float fallTicks;
    private float intensity;
    private double time;
    private double lastNow = Double.NaN;
    private int color = -1;

    private EffectClock(float riseTicks, float fallTicks) {
        this.riseTicks = riseTicks;
        this.fallTicks = fallTicks;
    }

    /**
     * 取得与所有者关联的时钟。首次调用确定渐入与渐出时长。
     *
     * @param owner     用于查找时钟的所有者，按相等性查找
     * @param riseTicks 从零到完全可见的 Tick 数，应为有限正数
     * @param fallTicks 从完全可见到零的 Tick 数，应为有限正数
     * @return 可跨帧复用的效果时钟
     */
    public static EffectClock of(Object owner, float riseTicks, float fallTicks) {
        var clock = CLOCKS.get(owner);
        if (clock == null) {
            clock = new EffectClock(riseTicks, fallTicks);
            CLOCKS.put(owner, clock);
        }
        return clock;
    }

    /**
     * 推进效果。首次调用以当前时间为起点，时间倒退时本步保持原进度。
     * 暂停或掉帧后的单次步长最多按 20 Tick 计算。
     *
     * @param active 效果是否应当显现
     * @param now    当前动画时间，单位为 Tick，须为有限值
     */
    public void update(boolean active, double now) {
        double step = Double.isNaN(lastNow) ? 0 : Math.max(0, Math.min(now - lastNow, MAX_STEP));
        lastNow = now;
        if (active) intensity = (float) Math.min(1, intensity + step / riseTicks);
        else intensity = (float) Math.max(0, intensity - step / fallTicks);
        float speed = intensity * intensity * (3 - 2 * intensity);
        time += step * speed;
    }

    /** 当前强度大于零时返回真，包括渐出期间。 */
    public boolean visible() {
        return intensity > 0;
    }

    /** 返回 0～1 的当前效果强度。 */
    public float intensity() {
        return intensity;
    }

    /** 返回按效果强度推进的本地动画时间，单位为 Tick。 */
    public double time() {
        return time;
    }

    /**
     * 更新并读取效果颜色。使用 -1 表示没有新颜色，可在渐出时沿用最近颜色。
     *
     * @param current  新的低 24 位 RGB 或 -1
     * @param fallback 时钟尚未记录颜色时使用的默认值
     * @return 最新有效颜色，或指定的默认值
     */
    public int color(int current, int fallback) {
        if (current != -1) color = current;
        return color == -1 ? fallback : color;
    }
}
