package com.modularmc.mmclutils.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** 绘制原版界面中的渐变矩形并计算列表行和滚动条布局。 */
public final class GuiUtils {

    private static final float MIN_SCROLL_THUMB_HEIGHT = 6.0F;

    private GuiUtils() {}

    /** 用原版 GUI 渲染类型绘制从左到右的 ARGB 渐变，沿用当前姿态和裁剪范围。 */
    public static void fillHorizontalGradient(GuiGraphics graphics, int left, int top, int right, int bottom,
                                              int leftColor, int rightColor) {
        fillGradient(graphics, RenderType.gui(), left, top, right, bottom, leftColor, leftColor, rightColor, rightColor);
    }

    /** 使用指定的位置和颜色渲染类型绘制水平渐变。调用方传入两侧的 ARGB 颜色。 */
    public static void fillHorizontalGradient(GuiGraphics graphics, RenderType layer, int left, int top,
                                              int right, int bottom, int leftColor, int rightColor) {
        fillGradient(graphics, layer, left, top, right, bottom, leftColor, leftColor, rightColor, rightColor);
    }

    /** 渲染类型须使用位置和颜色顶点格式。颜色依次对应左上、左下、右下和右上。 */
    public static void fillGradient(GuiGraphics graphics, RenderType layer, int left, int top, int right, int bottom,
                                    int topLeft, int bottomLeft, int bottomRight, int topRight) {
        if (left >= right || top >= bottom) return;
        var pose = graphics.pose().last().pose();
        VertexConsumer vertices = graphics.bufferSource().getBuffer(layer);
        vertices.addVertex(pose, left, top, 0).setColor(topLeft);
        vertices.addVertex(pose, left, bottom, 0).setColor(bottomLeft);
        vertices.addVertex(pose, right, bottom, 0).setColor(bottomRight);
        vertices.addVertex(pose, right, top, 0).setColor(topRight);
        graphics.flush();
    }

    /** 把零到一的列表进度换算成整像素宽度，最大值比行宽少一像素以保留行边界。 */
    public static int listProgressWidth(int rowWidth, float progress) {
        if (!Float.isFinite(progress) || progress < 0.0F || progress > 1.0F) {
            throw new IllegalArgumentException("进度应当在零与一之间");
        }
        return (int) Math.floor(progress * (rowWidth - 1));
    }

    /** 用轨道高度将六像素的最小滑块换算为百分比。轨道高度为零时直接返回原比例。 */
    public static float scrollbarThumbPercent(float naturalPercent, float trackHeight, boolean overflowing) {
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
