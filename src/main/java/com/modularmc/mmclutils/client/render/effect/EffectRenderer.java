package com.modularmc.mmclutils.client.render.effect;

import com.modularmc.mmclutils.ModularMCLibUtils;
import com.modularmc.mmclutils.client.render.effect.ParticleRing.RingShape;
import com.modularmc.mmclutils.client.render.effect.ParticleRing.RingStyle;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

/**
 * 在客户端渲染线程收集环形和连续粒子流，在不透明世界几何体之后批量绘制。
 * 实体附着和球形高亮在半透明粒子之后提交，时间推进由客户端 Tick 驱动。
 */
@EventBusSubscriber(modid = ModularMCLibUtils.MOD_ID, value = Dist.CLIENT)
public final class EffectRenderer {

    private static final ContextKey<Long> FRAME = new ContextKey<>(ModularMCLibUtils.id("effect_frame"));
    private static final ObjectArrayList<RingCommand> RINGS = new ObjectArrayList<>();
    private static final ObjectArrayList<Source> SOURCES = new ObjectArrayList<>();
    private static final Matrix4f MODEL_VIEW = new Matrix4f();
    private static int ringCount;
    private static long frame;
    private static @Nullable ClientLevel collectingLevel;
    private static double frameTime;
    private static long clientTicks;
    private static @Nullable ClientLevel lastLevel;
    private static @Nullable Camera camera;

    private EffectRenderer() {}

    static final class RingCommand {

        ParticleRing ring;
        float x, y, z;
        Direction.Axis axis;
        double time;
        float scale;
        int[] palette;
        float spriteSize;
        int spriteStrength;
        RingStyle style;
        int baseColor;
        float intensity;
        RingShape shape;
    }

    /**
     * 检查当前是否在指定世界的帧收集回调中。
     *
     * @param level 欲提交效果的世界
     * @return 当前帧正在此世界采集时为 {@code true}
     */
    public static boolean collecting(Level level) {
        return open() && collectingLevel == level;
    }

    static boolean open() {
        return collectingLevel != null;
    }

    /**
     * 添加跨帧使用的采集入口。相同入口只登记一次，须在采集回调之外调用。
     *
     * @param source 提交环形或连续粒子流的回调
     */
    public static void addSource(Source source) {
        if (!SOURCES.contains(source)) SOURCES.add(source);
    }

    /**
     * 移除先前添加的采集入口。已经采集的命令仍可在当帧绘制。
     *
     * @param source 要移除的回调
     */
    public static void removeSource(Source source) {
        SOURCES.remove(source);
    }

    /**
     * 读取客户端效果时钟，以 Tick 计并包含当前 Tick 内插值。
     *
     * @return 当前客户端时间，未进入世界时为 NaN
     */
    public static double now() {
        Minecraft client = Minecraft.getInstance();
        return client.level == null ? Double.NaN : clientTicks + client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    /** 返回最近一次世界帧采集使用的效果时间，单位为 Tick。 */
    public static double frameTime() {
        return frameTime;
    }

    /** 返回上一次提交给绘制管线的光点数。变换反馈路径以进入反馈的实例数计。 */
    public static int lastMoteCount() {
        return GpuParticleRenderer.lastDrawn();
    }

    /** 返回当前帧尚未过期的光点记录数，包含连续粒子流。 */
    public static int activeTimedEffects() {
        return GpuParticleRenderer.live(frameTime);
    }

    static Matrix4fc modelView() {
        return MODEL_VIEW;
    }

    static float breathe(RingCommand command) {
        return 0.9f + 0.1f * Mth.sin((float) (command.time * 0.21 % (2 * Math.PI)));
    }

    /**
     * 在 {@link Source#collect} 中提交完整闭环。参数遵循带 {@link RingShape} 的重载。
     *
     * @param ring      可复用的粒子运动数据
     * @param style     发光精灵与光带参数
     * @param worldX    中心世界坐标 X
     * @param worldY    中心世界坐标 Y
     * @param worldZ    中心世界坐标 Z
     * @param axis      环的轴线
     * @param time      环的动画时间，单位为 Tick
     * @param intensity 效果强度，范围为 0～1
     * @param baseColor 光带的 RGB
     * @param palette   粒子色板，至少含 {@link EffectPalette#SIZE} 种颜色
     */
    public static void ring(ParticleRing ring, RingStyle style, double worldX, double worldY, double worldZ, Direction.Axis axis,
                            double time, float intensity, int baseColor, int[] palette) {
        ring(ring, style, RingShape.CLOSED, worldX, worldY, worldZ, axis, time, intensity, baseColor, palette);
    }

    /**
     * 提交闭环或螺旋。供 {@link Source#collect} 调用，帧采集之外提交时直接返回。
     * 渲染器在当帧读取色板，调用方须保持其内容稳定。
     *
     * @param ring      可复用的粒子运动数据
     * @param style     发光精灵与光带参数
     * @param shape     环或多股螺旋的轮廓
     * @param worldX    原点世界坐标 X
     * @param worldY    原点世界坐标 Y
     * @param worldZ    原点世界坐标 Z
     * @param axis      环的轴线
     * @param time      环的动画时间，单位为 Tick
     * @param intensity 效果强度，范围为 0～1
     * @param baseColor 光带的 RGB
     * @param palette   粒子色板，至少含 {@link EffectPalette#SIZE} 种颜色
     * @throws IllegalArgumentException 正在采集且色板长度不足
     */
    public static void ring(ParticleRing ring, RingStyle style, RingShape shape, double worldX, double worldY, double worldZ,
                            Direction.Axis axis, double time, float intensity, int baseColor, int[] palette) {
        if (!open()) return;
        if (palette.length < EffectPalette.SIZE) throw new IllegalArgumentException("环的色板长度不足");
        if (ringCount == RINGS.size()) RINGS.add(new RingCommand());
        RingCommand command = RINGS.get(ringCount++);
        Vec3 eye = camera.position();
        float scale = intensity * intensity * (3 - 2 * intensity);
        command.ring = ring;
        command.x = (float) (worldX - eye.x);
        command.y = (float) (worldY - eye.y);
        command.z = (float) (worldZ - eye.z);
        command.axis = axis;
        command.time = time;
        command.scale = scale;
        command.palette = palette;
        command.spriteSize = ParticleRing.PIXEL * style.spriteScale() * scale;
        command.spriteStrength = (int) (style.spriteStrength() * intensity);
        command.style = style;
        command.baseColor = baseColor;
        command.intensity = intensity;
        command.shape = shape;
    }

    @SubscribeEvent
    private static void collectFrame(ExtractLevelRenderStateEvent event) {
        frameTime = now();
        camera = event.getCamera();
        ringCount = 0;
        PlayerAttachmentRenderer.beginFrame(event.getRenderState().cameraRenderState.pos);
        GpuParticleRenderer.beginFrame();
        event.getRenderState().setRenderData(FRAME, ++frame);
        Vec3 eye = camera.position();
        ClientLevel level = event.getLevel();
        collectingLevel = level;
        try {
            for (int index = 0; index < SOURCES.size(); index++) {
                SOURCES.get(index).collect(level, frameTime, eye.x, eye.y, eye.z);
            }
        } finally {
            collectingLevel = null;
        }
    }

    private static boolean matchesFrame(RenderLevelStageEvent event) {
        Long extracted = event.getLevelRenderState().getRenderData(FRAME);
        return extracted != null && extracted.longValue() == frame && camera != null;
    }

    @SubscribeEvent
    private static void drawWorldEffects(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        if (!matchesFrame(event)) return;
        EffectRenderPipelines.ensureTextures();
        MODEL_VIEW.set(event.getModelViewMatrix());
        if (ringCount != 0) {
            RingRenderer.draw(RINGS, ringCount, camera);
            ringCount = 0;
        }
        GpuParticleRenderer.flush(frameTime, camera);
    }

    @SubscribeEvent
    private static void drawAttachmentsAndHighlights(RenderLevelStageEvent.AfterTranslucentParticles event) {
        if (!matchesFrame(event)) return;
        PlayerAttachmentRenderer.drawPending();
        SphereHighlight.draw(camera.position(), MODEL_VIEW);
    }

    @SubscribeEvent
    private static void tickEffects(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level != lastLevel) {
            clearWorldEffects();
            lastLevel = level;
        }
        if (level == null || client.isPaused()) return;
        clientTicks++;
        SphereHighlight.tick();
        ParticleEmitters.tick(level);
    }

    private static void clearWorldEffects() {
        GpuParticleRenderer.clear();
        ParticleEmitters.clear();
        PlayerAttachmentRenderer.clearFrame();
        PlayerAttachmentRenderer.releaseBuffers();
        SphereHighlight.clear();
        RINGS.clear();
        camera = null;
        ringCount = 0;
    }

    /** 将管线、资源重载和玩家模型层接入客户端模组总线。 */
    @ApiStatus.Internal
    public static void init(IEventBus modBus) {
        modBus.addListener(EffectRenderPipelines::init);
        modBus.addListener(EffectRenderer::initResourceReload);
        modBus.addListener(PlayerAttachmentRenderer::initLayers);
    }

    private static void initResourceReload(AddClientReloadListenersEvent event) {
        event.addListener(ModularMCLibUtils.id("effects"), (ResourceManagerReloadListener) manager -> releaseRenderBuffers());
    }

    private static void releaseRenderBuffers() {
        MoteSprite.generation++;
        GpuParticleRenderer.reload();
        RingRenderer.reload();
        PlayerAttachmentRenderer.releaseBuffers();
        SphereHighlight.release();
        EffectRenderPipelines.releaseTextures();
    }

    @SubscribeEvent
    private static void clearOnDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        clearWorldEffects();
        lastLevel = null;
    }

    @SubscribeEvent
    private static void releaseOnShutdown(GameShuttingDownEvent event) {
        clearWorldEffects();
        releaseRenderBuffers();
    }

    /**
     * 在世界状态提取时提交一帧的绘制指令。
     * 回调在客户端渲染线程执行，可利用世界和相机状态决定要提交的效果。
     */
    @FunctionalInterface
    public interface Source {

        /**
         * 采集本帧的效果。请在回调返回前完成命令提交。
         *
         * @param level 本次绘制的客户端世界
         * @param time  动画时间，单位为 Tick
         * @param camX  相机世界坐标 X
         * @param camY  相机世界坐标 Y
         * @param camZ  相机世界坐标 Z
         */
        void collect(ClientLevel level, double time, double camX, double camY, double camZ);
    }
}
