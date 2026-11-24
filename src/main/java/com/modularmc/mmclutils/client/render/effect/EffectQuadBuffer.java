package com.modularmc.mmclutils.client.render.effect;

import com.modularmc.mmclutils.client.render.effect.EffectRenderPipelines.Blend;
import com.modularmc.mmclutils.client.render.effect.EffectRenderPipelines.Pass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/** 变换反馈和普通顶点写入共享相同的缓冲布局。 */
final class EffectQuadBuffer implements AutoCloseable {

    static final int VERTEX_BYTES = 9 * Float.BYTES;
    private static final Vector4f WHITE = new Vector4f(1);
    private static final Matrix4f POSE = new Matrix4f();

    private int array;
    private long capacity;
    int buffer;
    int vertices;

    void reserve(int vertexCapacity) {
        long bytes = (long) vertexCapacity * VERTEX_BYTES;
        if (buffer != 0 && capacity >= bytes) return;
        RenderSystem.assertOnRenderThread();
        if (buffer == 0) buffer = GL15.glGenBuffers();
        int previous = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        try {
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, Math.max(bytes, VERTEX_BYTES), GL15.GL_DYNAMIC_DRAW);
            capacity = Math.max(bytes, VERTEX_BYTES);
        } finally {
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, previous);
        }
    }

    void upload(ByteBuffer bytes) {
        int count = bytes.remaining() / VERTEX_BYTES;
        if (count != 0) {
            reserve(count);
            int previous = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
            try {
                GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, bytes);
            } finally {
                GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, previous);
            }
        }
        vertices = count;
    }

    void draw(Pass pass, ResourceLocation textureId) {
        draw(pass, textureId, EffectRenderer.modelView(), WHITE);
    }

    void draw(Pass pass, ResourceLocation textureId, Matrix4fc pose) {
        draw(pass, textureId, pose, WHITE);
    }

    void draw(Pass pass, ResourceLocation textureId, Matrix4fc pose, Vector4fc tint) {
        if (vertices == 0) return;
        RenderSystem.assertOnRenderThread();
        var mc = Minecraft.getInstance();
        var shader = GameRenderer.getPositionTexColorShader();
        int previousArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int activeTexture = GlStateManager._getActiveTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._activeTexture(activeTexture);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), targetRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), targetAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);

        try {
            configure(pass);
            shader.setDefaultUniforms(VertexFormat.Mode.QUADS, POSE.set(pose), EffectRenderer.projection(), mc.getWindow());
            shader.setSampler("Sampler0", mc.getTextureManager().getTexture(textureId));
            shader.COLOR_MODULATOR.set(tint.x(), tint.y(), tint.z(), tint.w());
            shader.apply();
            bind();
            int indexCount = vertices / 4 * 6;
            var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
            indices.bind(indexCount);
            RenderSystem.drawElements(GL11.GL_TRIANGLES, indexCount, indices.type().asGLType);
        } finally {
            shader.clear();
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(previousTexture);
            GlStateManager._activeTexture(activeTexture);
            GlStateManager._glBindVertexArray(previousArray);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
            if (depth) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            RenderSystem.depthFunc(depthFunction);
            RenderSystem.depthMask(depthWrite);
            if (cull) RenderSystem.enableCull();
            else RenderSystem.disableCull();
            if (blend) RenderSystem.enableBlend();
            else RenderSystem.disableBlend();
            RenderSystem.blendFuncSeparate(sourceRgb, targetRgb, sourceAlpha, targetAlpha);
        }
    }

    private void bind() {
        if (array != 0) {
            GlStateManager._glBindVertexArray(array);
            return;
        }
        array = GL30.glGenVertexArrays();
        GlStateManager._glBindVertexArray(array);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        // 原版着色器的属性顺序是 Position、UV0、Color。反馈缓冲的颜色使用四个浮点数。
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, VERTEX_BYTES, 0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, VERTEX_BYTES, 7L * Float.BYTES);
        GL20.glVertexAttribPointer(2, 4, GL11.GL_FLOAT, false, VERTEX_BYTES, 3L * Float.BYTES);
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glEnableVertexAttribArray(2);
    }

    private static void configure(Pass pass) {
        if (pass.depthTest()) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthMask(pass.writeDepth());
        RenderSystem.disableCull();
        if (pass.blend() == Blend.OPAQUE) {
            RenderSystem.disableBlend();
        } else {
            RenderSystem.enableBlend();
            if (pass.blend() == Blend.ADDITIVE) RenderSystem.blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
            else RenderSystem.defaultBlendFunc();
        }
    }

    @Override
    public void close() {
        if (array != 0) GlStateManager._glDeleteVertexArrays(array);
        if (buffer != 0) GlStateManager._glDeleteBuffers(buffer);
        array = 0;
        buffer = 0;
        vertices = 0;
        capacity = 0;
    }
}
