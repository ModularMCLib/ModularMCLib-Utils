package com.modularmc.mmclutils.client.render.effect;

import com.modularmc.mmclutils.ModularMCLibUtils;

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

/** 每帧收集环和线流命令，在世界渲染阶段批量提交。 */
@EventBusSubscriber(modid = ModularMCLibUtils.MOD_ID, value = Dist.CLIENT)
public final class EffectRenderer {

    private static final ContextKey<Long> FRAME = new ContextKey<>(ModularMCLibUtils.id("effect_frame"));
    private static final ObjectArrayList<RingCommand> RINGS = new ObjectArrayList<>();
    private static final ObjectArrayList<EffectSource> SOURCES = new ObjectArrayList<>();
    private static final Matrix4f MODEL_VIEW = new Matrix4f();
    private static int ringCount;
    private static long frame;
    private static boolean collecting;
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

    public static boolean collecting(Level level) {
        return collecting && level == lastLevel;
    }

    static boolean open() {
        return collecting;
    }

    public static void addSource(EffectSource source) {
        if (!SOURCES.contains(source)) SOURCES.add(source);
    }

    public static void removeSource(EffectSource source) {
        SOURCES.remove(source);
    }

    public static double now() {
        Minecraft client = Minecraft.getInstance();
        return client.level == null ? Double.NaN : clientTicks + client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    public static double frameTime() {
        return frameTime;
    }

    public static int lastMoteCount() {
        return GpuParticleRenderer.lastDrawn();
    }

    public static int activeTimedEffects() {
        return GpuParticleRenderer.live(frameTime);
    }

    static Matrix4fc modelView() {
        return MODEL_VIEW;
    }

    static float breathe(RingCommand command) {
        return 0.9f + 0.1f * Mth.sin((float) (command.time * 0.21 % (2 * Math.PI)));
    }

    public static void ring(ParticleRing ring, RingStyle style, double worldX, double worldY, double worldZ, Direction.Axis axis,
                            double time, float intensity, int baseColor, int[] palette) {
        ring(ring, style, RingShape.CLOSED, worldX, worldY, worldZ, axis, time, intensity, baseColor, palette);
    }

    public static void ring(ParticleRing ring, RingStyle style, RingShape shape, double worldX, double worldY, double worldZ,
                            Direction.Axis axis, double time, float intensity, int baseColor, int[] palette) {
        if (!collecting) return;
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
        PlayerAttachmentRenderer.beginFrame();
        GpuParticleRenderer.beginFrame();
        event.getRenderState().setRenderData(FRAME, ++frame);
        Vec3 eye = camera.position();
        collecting = true;
        try {
            for (int index = 0; index < SOURCES.size(); index++) {
                SOURCES.get(index).collect(event.getLevel(), frameTime, eye.x, eye.y, eye.z);
            }
        } finally {
            collecting = false;
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
        SphereHighlight.clear();
        RINGS.clear();
        camera = null;
        ringCount = 0;
    }

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
        SphereHighlight.release();
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
}
