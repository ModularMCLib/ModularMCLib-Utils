package com.modularmc.mmclutils.client.render.effect;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * 采集附着在玩家身体部件上的效果，在半透明粒子之后用场景深度绘制像素片。
 * 回调可以通过当帧模型姿态定位身体部件，并将局部位置转换为世界坐标。
 * 绘制、回调登记和 GPU 资源释放都在客户端渲染线程执行。
 */
public final class PlayerAttachmentRenderer {

    private static final ObjectArrayList<Attachment> LISTENERS = new ObjectArrayList<>();
    private static final ObjectArrayList<Draw> PENDING = new ObjectArrayList<>();
    private static final ReferenceOpenHashSet<PixelSheet> UPLOADED_SHEETS = new ReferenceOpenHashSet<>();
    private static final Vector3f TRANSFORMED_POSITION = new Vector3f();
    private static final Matrix4f DRAW_MATRIX = new Matrix4f();
    private static @Nullable Vec3 cameraOrigin;
    private static int count;

    private PlayerAttachmentRenderer() {}

    /**
     * 登记一个对所有玩家实体生效的附着回调。相同回调只登记一次。
     *
     * @param attachment 模型姿态采集回调
     */
    public static void add(Attachment attachment) {
        if (!LISTENERS.contains(attachment)) LISTENERS.add(attachment);
    }

    /**
     * 移除附着回调。请在附着回调执行之外调用。
     *
     * @param attachment 先前登记的回调
     */
    public static void remove(Attachment attachment) {
        LISTENERS.remove(attachment);
    }

    static void initLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) renderer.addLayer(new Layer(renderer));
        }
    }

    static void beginFrame(Vec3 origin) {
        clearFrame();
        cameraOrigin = origin;
    }

    static void clearFrame() {
        for (int i = 0; i < count; i++) PENDING.get(i).sheet = null;
        count = 0;
        cameraOrigin = null;
    }

    static void releaseBuffers() {
        for (PixelSheet sheet : UPLOADED_SHEETS) sheet.releaseBuffers();
        UPLOADED_SHEETS.clear();
    }

    static void drawPending() {
        try {
            for (int i = 0; i < count; i++) {
                var draw = PENDING.get(i);
                draw.sheet.draw(DRAW_MATRIX.set(EffectRenderer.modelView()).mul(draw.transform), draw.tint);
            }
        } finally {
            clearFrame();
        }
    }

    /**
     * 查询当前能否提交玩家附着效果。
     *
     * @return 世界帧已开始采集且尚未提交附着绘制时为 {@code true}
     */
    public static boolean inLevel() {
        return cameraOrigin != null;
    }

    /**
     * 按像素片自身颜色提交绘制。
     *
     * @param sheet 可复用的像素片
     * @param poses 已定位到相应身体部件的姿态栈
     */
    public static void draw(PixelSheet sheet, PoseStack poses) {
        draw(sheet, poses, 1, 1, 1);
    }

    /**
     * 将当前姿态复制到本帧命令中，并在提交时对像素颜色乘以指定染色分量。
     * 应在 {@link Attachment#collect} 中调用。世界帧之外调用时直接返回。
     *
     * @param sheet 可复用的像素片
     * @param poses 已定位到相应身体部件的姿态栈
     * @param red   红色倍率
     * @param green 绿色倍率
     * @param blue  蓝色倍率
     */
    public static void draw(PixelSheet sheet, PoseStack poses, float red, float green, float blue) {
        if (!inLevel()) return;
        if (count == PENDING.size()) PENDING.add(new Draw());
        Draw entry = PENDING.get(count++);
        entry.sheet = sheet;
        entry.transform.set(poses.last().pose());
        entry.tint.set(red, green, blue, 1);
    }

    /**
     * 用模型姿态和当帧相机原点，将身体部件上的局部点转换到世界空间。
     * 应在 {@link Attachment#collect} 中用已定位到部件的姿态调用。
     *
     * @param poses 含相机相对平移的实体姿态栈
     * @param x     局部 X 坐标，单位为方块
     * @param y     局部 Y 坐标，单位为方块
     * @param z     局部 Z 坐标，单位为方块
     * @param out   接收世界坐标的向量。世界帧之外调用时保留原值
     * @return 是否写入了世界坐标
     */
    public static boolean toWorld(PoseStack poses, float x, float y, float z, Vector3d out) {
        Vec3 origin = cameraOrigin;
        if (origin == null) return false;
        var relative = poses.last().pose().transformPosition(x, y, z, TRANSFORMED_POSITION);
        out.set(origin.x + relative.x, origin.y + relative.y, origin.z + relative.z);
        return true;
    }

    /**
     * 在渲染线程上采集玩家附着效果，复用已完成动画的模型姿态。
     * 可以暂时变换姿态栈来定位身体部件，返回前须恢复原有姿态。
     * 模型和姿态栈由原版复用，只能在当次调用中使用。
     */
    @FunctionalInterface
    public interface Attachment {

        /**
         * 采集单个玩家的附着效果。
         *
         * @param player 当前正在渲染的客户端玩家
         * @param model  已应用动画的玩家模型
         * @param pose   实体相对相机的姿态栈
         */
        void collect(AbstractClientPlayer player, PlayerModel<AbstractClientPlayer> model, PoseStack pose);
    }

    private static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

        private Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float yaw, float pitch) {
            if (!inLevel()) return;
            for (int i = 0; i < LISTENERS.size(); i++) LISTENERS.get(i).collect(player, getParentModel(), pose);
        }
    }

    private static final class Draw {

        private PixelSheet sheet;
        private final Matrix4f transform = new Matrix4f();
        private final Vector4f tint = new Vector4f(1);
    }

    /**
     * 位于局部 XY 平面的不可变发光像素片。位置与边长均以方块为单位。
     * 首次绘制时上传静态几何，可在不同玩家和姿态上复用。
     * 重载资源、退出世界或显式关闭时释放 GPU 缓冲，下次绘制会重新上传。
     */
    public static final class PixelSheet implements AutoCloseable {

        private static final int STRIDE = 4;
        private final float[] pixels;
        private final float pixel;
        private final float halo;
        private final float core;
        private final EffectQuadBuffer glow = new EffectQuadBuffer();
        private final EffectQuadBuffer center = new EffectQuadBuffer();
        private boolean uploaded;

        private PixelSheet(float[] pixels, float pixel, float halo, float core) {
            this.pixels = pixels;
            this.pixel = pixel;
            this.halo = halo;
            this.core = core;
        }

        /**
         * 创建像素片构建器。光晕边长为 {@code pixel * halo}，亮芯边长为 {@code pixel * halo * core}。
         *
         * @param pixel 基础像素边长，单位为方块
         * @param halo  光晕相对基础像素的边长倍率
         * @param core  亮芯相对光晕的边长倍率
         * @return 独立的构建器
         */
        public static Builder builder(float pixel, float halo, float core) {
            return new Builder(pixel, halo, core);
        }

        /** 返回像素数量。 */
        public int count() {
            return pixels.length / STRIDE;
        }

        /**
         * 读取像素中心的局部 X 坐标。
         *
         * @param index 像素索引，范围为 0 到 {@link #count()} 减一
         * @return 以方块为单位的 X 坐标
         */
        public float x(int index) {
            return pixels[index * STRIDE];
        }

        /**
         * 读取像素中心的局部 Y 坐标。
         *
         * @param index 像素索引，范围为 0 到 {@link #count()} 减一
         * @return 以方块为单位的 Y 坐标
         */
        public float y(int index) {
            return pixels[index * STRIDE + 1];
        }

        /**
         * 读取构建时传入的颜色。
         *
         * @param index 像素索引，范围为 0 到 {@link #count()} 减一
         * @return 打包在低 24 位的 RGB
         */
        public int rgb(int index) {
            return Float.floatToRawIntBits(pixels[index * STRIDE + 2]);
        }

        /** 返回基础像素边长，单位为方块。 */
        public float pixel() {
            return pixel;
        }

        private void draw(Matrix4f transform, Vector4f tint) {
            if (!uploaded) upload();
            Matrix4f matrix = new Matrix4f(EffectRenderer.modelView()).mul(transform);
            glow.draw(EffectRenderPipelines.GLOW, EffectRenderPipelines.MOTE_TEXTURE, matrix, tint);
            center.draw(EffectRenderPipelines.GLOW, EffectRenderPipelines.WHITE_TEXTURE, matrix, tint);
        }

        private void upload() {
            var vertices = new EffectVertices();
            try {
                float haloSize = pixel * halo;
                upload(vertices, glow, haloSize, 0);
                upload(vertices, center, haloSize * core, 0.55f);
                UPLOADED_SHEETS.add(this);
                uploaded = true;
            } catch (RuntimeException | Error exception) {
                releaseBuffers();
                throw exception;
            } finally {
                vertices.release();
            }
        }

        private void upload(EffectVertices vertices, EffectQuadBuffer output, float size, float whiten) {
            vertices.begin(count());
            float half = size * 0.5f;
            for (int i = 0; i < pixels.length; i += STRIDE) {
                float x = pixels[i], y = pixels[i + 1];
                int color = Float.floatToRawIntBits(pixels[i + 2]);
                float alpha = pixels[i + 3];
                // 加法混合直接累加 RGB，由顶点颜色携带像素亮度。
                float r = Mth.lerp(whiten, ARGB32.red(color) / 255f, 1) * alpha;
                float g = Mth.lerp(whiten, ARGB32.green(color) / 255f, 1) * alpha;
                float b = Mth.lerp(whiten, ARGB32.blue(color) / 255f, 1) * alpha;
                vertices.writeVertex(x - half, y - half, 0, r, g, b, alpha, 0, 0);
                vertices.writeVertex(x - half, y + half, 0, r, g, b, alpha, 0, 1);
                vertices.writeVertex(x + half, y + half, 0, r, g, b, alpha, 1, 1);
                vertices.writeVertex(x + half, y - half, 0, r, g, b, alpha, 1, 0);
            }
            vertices.upload(output);
        }

        /** 在渲染线程释放已上传的缓冲。CPU 侧像素仍可再次绘制。 */
        @Override
        public void close() {
            UPLOADED_SHEETS.remove(this);
            releaseBuffers();
        }

        private void releaseBuffers() {
            glow.close();
            center.close();
            uploaded = false;
        }

        /** 按插入顺序累积像素。每次构建均取得独立的像素数据。 */
        public static final class Builder {

            private final FloatArrayList pixels = new FloatArrayList();
            private final float pixel;
            private final float halo;
            private final float core;

            private Builder(float pixel, float halo, float core) {
                this.pixel = pixel;
                this.halo = halo;
                this.core = core;
            }

            /**
             * 追加一个像素。
             *
             * @param x     局部 X 坐标，单位为方块
             * @param y     局部 Y 坐标，单位为方块
             * @param rgb   低 24 位的 RGB
             * @param alpha 介于 0 和 1 之间的像素亮度
             * @return 当前构建器
             */
            public Builder pixel(float x, float y, int rgb, float alpha) {
                pixels.add(x);
                pixels.add(y);
                pixels.add(Float.intBitsToFloat(rgb & 0xFFFFFF));
                pixels.add(alpha);
                return this;
            }

            /** 复制当前像素数据并构造一张可独立使用的像素片。 */
            public PixelSheet build() {
                return new PixelSheet(pixels.toFloatArray(), pixel, halo, core);
            }
        }
    }
}
