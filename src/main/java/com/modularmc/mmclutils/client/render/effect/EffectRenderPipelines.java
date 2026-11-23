package com.modularmc.mmclutils.client.render.effect;

import com.modularmc.mmclutils.ModularMCLibUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** 环、光点和实体附着共用的原版绘制状态与纹理。 */
public final class EffectRenderPipelines {

    public static final Identifier GLOW_TEXTURE = ModularMCLibUtils.id("effect/glow");
    public static final Identifier WHITE_TEXTURE = ModularMCLibUtils.id("effect/white");
    public static final Identifier MOTE_TEXTURE = ModularMCLibUtils.id("effect/mote");

    private static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.register(
                    VertexFormatElement.findNextId(), 0, VertexFormatElement.Type.FLOAT, false, 4))
            .add("UV0", VertexFormatElement.UV0)
            .build();

    public static final RenderPipeline VOXEL = program("voxel", null, true, false);
    public static final RenderPipeline GLOW = program("glow", BlendFunction.ADDITIVE, false, false);
    public static final RenderPipeline HIGHLIGHT = program("highlight", BlendFunction.TRANSLUCENT, false, true);
    private static final RenderPipeline GLOW_THROUGH = program("glow_through", BlendFunction.ADDITIVE, false, true);
    private static final RenderPipeline ATLAS = program("particle", BlendFunction.TRANSLUCENT, false, false);
    private static final RenderPipeline ATLAS_THROUGH = program("particle_through", BlendFunction.TRANSLUCENT, false, true);
    private static boolean texturesReady;

    private EffectRenderPipelines() {}

    static void init(RegisterRenderPipelinesEvent event) {
        for (RenderPipeline pipeline : new RenderPipeline[] { VOXEL, GLOW, HIGHLIGHT, GLOW_THROUGH, ATLAS, ATLAS_THROUGH }) {
            event.registerPipeline(pipeline);
        }
    }

    public static RenderPipeline glow(boolean throughWalls) {
        return throughWalls ? GLOW_THROUGH : GLOW;
    }

    public static RenderPipeline atlas(boolean throughWalls) {
        return throughWalls ? ATLAS_THROUGH : ATLAS;
    }

    private static RenderPipeline program(String path, @Nullable BlendFunction blend, boolean writeDepth, boolean throughWalls) {
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(ModularMCLibUtils.id("pipeline/effect/" + path))
                .withVertexShader("core/position_tex_color")
                .withFragmentShader("core/position_tex_color")
                .withSampler("Sampler0")
                .withCull(false)
                .withDepthStencilState(throughWalls ? Optional.empty() : Optional.of(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, writeDepth)))
                .withColorTargetState(blend == null ? ColorTargetState.DEFAULT : new ColorTargetState(blend))
                .withVertexFormat(FORMAT, VertexFormat.Mode.QUADS)
                .build();
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
                glow.setPixel(x, y, alpha << 24 | 0xFFFFFF);
                mote.setPixel(x, y, alpha * 0x01010101);
            }
        }
        var white = new NativeImage(1, 1, false);
        white.setPixel(0, 0, -1);
        var textures = Minecraft.getInstance().getTextureManager();
        textures.register(GLOW_TEXTURE, new DynamicTexture(GLOW_TEXTURE::toString, glow));
        textures.register(MOTE_TEXTURE, new DynamicTexture(MOTE_TEXTURE::toString, mote));
        textures.register(WHITE_TEXTURE, new DynamicTexture(WHITE_TEXTURE::toString, white));
        texturesReady = true;
    }
}
