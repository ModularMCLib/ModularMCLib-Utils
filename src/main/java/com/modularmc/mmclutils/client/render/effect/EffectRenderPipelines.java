package com.modularmc.mmclutils.client.render.effect;

import com.modularmc.mmclutils.ModularMCLibUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import com.mojang.blaze3d.platform.NativeImage;

/** 环、光点和实体附着共用的原版绘制状态与纹理。 */
public final class EffectRenderPipelines {

    /** 渐隐的环形光带纹理。 */
    public static final ResourceLocation GLOW_TEXTURE = ModularMCLibUtils.id("effect/glow");
    /** 绘制纯色几何时使用的白色纹理。 */
    public static final ResourceLocation WHITE_TEXTURE = ModularMCLibUtils.id("effect/white");
    /** RGB 已按透明度渐变的光点纹理，供加法混合使用。 */
    public static final ResourceLocation MOTE_TEXTURE = ModularMCLibUtils.id("effect/mote");

    /** 使用场景深度测试与深度写入绘制实体四边形。 */
    public static final Pass VOXEL = new Pass(Blend.OPAQUE, true, true);
    /** 使用场景深度测试，以加法混合写入颜色。 */
    public static final Pass GLOW = new Pass(Blend.ADDITIVE, true, false);
    /** 以透明度混合绘制穿过场景的高亮。 */
    public static final Pass HIGHLIGHT = new Pass(Blend.TRANSLUCENT, false, false);
    private static final Pass GLOW_THROUGH = new Pass(Blend.ADDITIVE, false, false);
    private static final Pass ATLAS = new Pass(Blend.TRANSLUCENT, true, false);
    private static final Pass ATLAS_THROUGH = new Pass(Blend.TRANSLUCENT, false, false);
    private static boolean texturesReady;

    private EffectRenderPipelines() {}

    /** 选择对应遮挡规则的加法混合状态。传入真时跳过场景深度测试。 */
    public static Pass glow(boolean throughWalls) {
        return throughWalls ? GLOW_THROUGH : GLOW;
    }

    /** 选择对应遮挡规则的粒子图集状态，颜色按透明度混合。 */
    public static Pass atlas(boolean throughWalls) {
        return throughWalls ? ATLAS_THROUGH : ATLAS;
    }

    /** 1.21.1 位置、贴图、颜色着色器的绘制状态。 */
    public record Pass(Blend blend, boolean depthTest, boolean writeDepth) {}

    /** 场景颜色与效果颜色的合成规则。 */
    public enum Blend {
        OPAQUE,
        ADDITIVE,
        TRANSLUCENT
    }

    static void ensureTextures() {
        if (texturesReady) return;
        int size = 16;
        var glow = new NativeImage(size, size, false);
        var mote = new NativeImage(size, size, false);
        float center = (size - 1) / 2f;
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                float dx = (x - center) / (size / 2f);
                float dy = (y - center) / (size / 2f);
                float fade = Math.max(0, 1 - (float) Math.sqrt(dx * dx + dy * dy));
                int alpha = Math.round((float) Math.ceil(fade * fade * 5) * 51);
                glow.setPixelRGBA(x, y, alpha << 24 | 0xFFFFFF);
                mote.setPixelRGBA(x, y, alpha * 0x01010101);
            }
        }
        var white = new NativeImage(1, 1, false);
        white.setPixelRGBA(0, 0, -1);
        var textures = Minecraft.getInstance().getTextureManager();
        textures.register(GLOW_TEXTURE, new DynamicTexture(glow));
        textures.register(MOTE_TEXTURE, new DynamicTexture(mote));
        textures.register(WHITE_TEXTURE, new DynamicTexture(white));
        texturesReady = true;
    }

    static void releaseTextures() {
        if (!texturesReady) return;
        var textures = Minecraft.getInstance().getTextureManager();
        textures.release(GLOW_TEXTURE);
        textures.release(WHITE_TEXTURE);
        textures.release(MOTE_TEXTURE);
        texturesReady = false;
    }
}
