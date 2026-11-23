package com.modularmc.mmclutils.client.gui;

/** 计算列表行的进度宽度和滚动条滑块比例。 */
public final class ListLayout {

    private static final float MIN_SCROLL_THUMB_HEIGHT = 6.0F;

    private ListLayout() {}

    public static int progressWidth(int rowWidth, float progress) {
        if (!Float.isFinite(progress) || progress < 0.0F || progress > 1.0F) {
            throw new IllegalArgumentException("进度应当在零与一之间");
        }
        return (int) Math.floor(progress * (rowWidth - 1));
    }

    public static float thumbPercent(float naturalPercent, float trackHeight, boolean overflowing) {
        if (!Float.isFinite(naturalPercent) || naturalPercent < 0.0F || naturalPercent > 100.0F) {
            throw new IllegalArgumentException("滚动条比例应当在零与一百之间");
        }
        if (!Float.isFinite(trackHeight) || trackHeight < 0.0F) {
            throw new IllegalArgumentException("滚动条轨道高度应为非负数");
        }
        if (trackHeight == 0.0F) {
            return naturalPercent;
        }
        float minimumPercent = Math.min(100.0F, MIN_SCROLL_THUMB_HEIGHT * 100.0F / trackHeight);
        return overflowing ? Math.max(naturalPercent, minimumPercent) : minimumPercent;
    }
}
