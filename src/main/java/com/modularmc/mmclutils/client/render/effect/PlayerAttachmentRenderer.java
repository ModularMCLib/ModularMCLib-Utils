package com.modularmc.mmclutils.client.render.effect;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** 复用玩家模型的实际姿态，延后绘制像素附着层。 */
public final class PlayerAttachmentRenderer {

    private static final ObjectArrayList<Attachment> LISTENERS = new ObjectArrayList<>();
    private static final ObjectArrayList<Draw> PENDING = new ObjectArrayList<>();
    private static int count;
    private static boolean active;

    private PlayerAttachmentRenderer() {}

    public static void add(Attachment attachment) {
        if (!LISTENERS.contains(attachment)) LISTENERS.add(attachment);
    }

    public static void remove(Attachment attachment) {
        LISTENERS.remove(attachment);
    }

    static void initLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            var renderer = event.getPlayerRenderer(skin);
            if (renderer != null) renderer.addLayer(new Layer(renderer));
        }
    }

    static void beginFrame() {
        clearFrame();
        active = true;
    }

    static void clearFrame() {
        for (int i = 0; i < count; i++) PENDING.get(i).sheet = null;
        count = 0;
        active = false;
    }

    static void drawPending() {
        try {
            for (int i = 0; i < count; i++) {
                var draw = PENDING.get(i);
                draw.sheet.draw(draw.transform, draw.tint);
            }
        } finally {
            clearFrame();
        }
    }

    public static void draw(PixelSheet sheet, PoseStack poses, float red, float green, float blue) {
        if (!active) return;
        if (count == PENDING.size()) PENDING.add(new Draw());
        Draw entry = PENDING.get(count++);
        entry.sheet = sheet;
        entry.transform.set(poses.last().pose());
        entry.tint.set(red, green, blue, 1);
    }

    /**
     * 在渲染线程上采集玩家附着效果，复用已完成动画的模型姿态。
     * 可以暂时变换 PoseStack 来定位身体部件，返回前须恢复原有姿态。
     */
    @FunctionalInterface
    public interface Attachment {

        void collect(AvatarRenderState state, PlayerModel model, PoseStack pose);
    }

    private static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {

        private Layer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
            super(parent);
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state,
                           float yaw, float pitch) {
            if (!active) return;
            for (int i = 0; i < LISTENERS.size(); i++) LISTENERS.get(i).collect(state, getParentModel(), pose);
        }
    }

    private static final class Draw {

        private PixelSheet sheet;
        private final Matrix4f transform = new Matrix4f();
        private final Vector4f tint = new Vector4f(1);
    }

    /** 不可变的本地像素面，可对多个实体复用。绘制资源须在客户端渲染线程释放。 */
    public static final class PixelSheet implements AutoCloseable {

        private final float[] pixels;
        private final float scale;
        private final float halo;
        private final float core;
        private final EffectQuadBuffer glow = new EffectQuadBuffer();
        private final EffectQuadBuffer center = new EffectQuadBuffer();
        private boolean uploaded;

        private PixelSheet(float[] pixels, float scale, float halo, float core) {
            this.pixels = pixels;
            this.scale = scale;
            this.halo = halo;
            this.core = core;
        }

        public static Builder builder(float scale, float halo, float core) {
            return new Builder(scale, halo, core);
        }

        public int count() {
            return pixels.length / 4;
        }

        private void draw(Matrix4f transform, Vector4f tint) {
            if (!uploaded) {
                upload(glow, halo);
                upload(center, core);
                uploaded = true;
            }
            Matrix4f matrix = new Matrix4f(EffectRenderer.modelView()).mul(transform);
            glow.draw(EffectRenderPipelines.GLOW, EffectRenderPipelines.GLOW_TEXTURE, matrix, tint);
            center.draw(EffectRenderPipelines.GLOW, EffectRenderPipelines.WHITE_TEXTURE, matrix, tint);
        }

        private void upload(EffectQuadBuffer output, float radius) {
            var vertices = new EffectVertices();
            vertices.begin(count());
            try {
                for (int i = 0; i < pixels.length; i += 4) {
                    float x = pixels[i] * scale, y = pixels[i + 1] * scale;
                    int color = Float.floatToRawIntBits(pixels[i + 2]);
                    float alpha = pixels[i + 3], size = radius * scale * .5f;
                    vertices.writeVertex(x - size, y - size, 0, color, alpha, 0, 0);
                    vertices.writeVertex(x - size, y + size, 0, color, alpha, 0, 1);
                    vertices.writeVertex(x + size, y + size, 0, color, alpha, 1, 1);
                    vertices.writeVertex(x + size, y - size, 0, color, alpha, 1, 0);
                }
                vertices.upload(output);
            } finally {
                vertices.release();
            }
        }

        @Override
        public void close() {
            glow.close();
            center.close();
            uploaded = false;
        }

        public static final class Builder {

            private final FloatArrayList pixels = new FloatArrayList();
            private final float scale;
            private final float halo;
            private final float core;

            private Builder(float scale, float halo, float core) {
                this.scale = scale;
                this.halo = halo;
                this.core = core;
            }

            public Builder pixel(float x, float y, int rgb, float alpha) {
                pixels.add(x);
                pixels.add(y);
                pixels.add(Float.intBitsToFloat(rgb & 0xFFFFFF));
                pixels.add(alpha);
                return this;
            }

            public PixelSheet build() {
                return new PixelSheet(pixels.toFloatArray(), scale, halo, core);
            }
        }
    }
}
