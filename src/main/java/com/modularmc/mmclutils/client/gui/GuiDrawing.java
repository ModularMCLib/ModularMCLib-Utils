package com.modularmc.mmclutils.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/** 使用原版界面提取入口绘制矩形。 */
public final class GuiDrawing {

    private GuiDrawing() {}

    public static void fillHorizontalGradient(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom,
                                              int leftColor, int rightColor) {
        fillGradient(graphics, RenderPipelines.GUI, left, top, right, bottom, leftColor, leftColor, rightColor, rightColor);
    }

    public static void fillHorizontalGradient(GuiGraphicsExtractor graphics, RenderPipeline pipeline, int left, int top,
                                              int right, int bottom, int leftColor, int rightColor) {
        fillGradient(graphics, pipeline, left, top, right, bottom, leftColor, leftColor, rightColor, rightColor);
    }

    /** 管线须使用位置和颜色顶点格式。颜色依次对应左上、左下、右下和右上。 */
    public static void fillGradient(GuiGraphicsExtractor graphics, RenderPipeline pipeline, int left, int top, int right, int bottom,
                                    int topLeft, int bottomLeft, int bottomRight, int topRight) {
        if (left >= right || top >= bottom) return;
        var pose = new Matrix3x2f(graphics.pose());
        ScreenRectangle scissor = graphics.peekScissorStack();
        ScreenRectangle bounds = new ScreenRectangle(left, top, right - left, bottom - top).transformMaxBounds(pose);
        if (scissor != null) bounds = bounds.intersection(scissor);
        if (bounds == null) return;
        graphics.submitGuiElementRenderState(
                new Gradient(pipeline, pose, scissor, bounds, left, top, right, bottom, topLeft, bottomLeft, bottomRight, topRight));
    }

    private record Gradient(RenderPipeline pipeline, Matrix3x2fc pose, @Nullable ScreenRectangle scissorArea, ScreenRectangle bounds,
                            int left, int top, int right, int bottom, int topLeft, int bottomLeft, int bottomRight, int topRight)
            implements GuiElementRenderState {

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.noTexture();
        }

        @Override
        public void buildVertices(VertexConsumer vertices) {
            vertices.addVertexWith2DPose(pose, left, top).setColor(topLeft);
            vertices.addVertexWith2DPose(pose, left, bottom).setColor(bottomLeft);
            vertices.addVertexWith2DPose(pose, right, bottom).setColor(bottomRight);
            vertices.addVertexWith2DPose(pose, right, top).setColor(topRight);
        }
    }
}
