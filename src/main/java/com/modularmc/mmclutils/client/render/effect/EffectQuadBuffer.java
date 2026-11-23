package com.modularmc.mmclutils.client.render.effect;

import com.modularmc.mmclutils.mixin.client.render.GlBufferAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** 变换反馈和普通顶点写入共享相同的缓冲布局。 */
final class EffectQuadBuffer implements AutoCloseable {

    static final int VERTEX_BYTES = 9 * Float.BYTES;
    private static final Vector4f WHITE = new Vector4f(1);
    private static final Vector3f ZERO = new Vector3f();
    private static final Matrix4f IDENTITY = new Matrix4f();

    private @Nullable GpuBuffer verticesBuffer;
    int buffer;
    int vertices;

    void reserve(int vertexCapacity) {
        long bytes = (long) vertexCapacity * VERTEX_BYTES;
        if (verticesBuffer != null && verticesBuffer.size() >= bytes) return;
        close();
        verticesBuffer = RenderSystem.getDevice().createBuffer(
                () -> "mmclutils-effects", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, Math.max(bytes, VERTEX_BYTES));
        if (verticesBuffer instanceof GlBufferAccessor accessor) buffer = accessor.mmclutils$handle();
    }

    void upload(ByteBuffer bytes) {
        int count = bytes.remaining() / VERTEX_BYTES;
        if (count != 0) {
            reserve(count);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(verticesBuffer.slice(0, bytes.remaining()), bytes);
        }
        vertices = count;
    }

    void draw(RenderPipeline pipeline, Identifier textureId) {
        draw(pipeline, textureId, EffectRenderer.modelView(), WHITE);
    }

    void draw(RenderPipeline pipeline, Identifier textureId, Matrix4fc pose) {
        draw(pipeline, textureId, pose, WHITE);
    }

    void draw(RenderPipeline pipeline, Identifier textureId, Matrix4fc pose, Vector4fc tint) {
        if (vertices == 0) return;
        var mc = Minecraft.getInstance();
        var target = mc.getMainRenderTarget();
        var color = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
        var depth = RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView();
        var texture = mc.getTextureManager().getTexture(textureId);
        int indexCount = vertices / 4 * 6;
        var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        var indexBuffer = indices.getBuffer(indexCount);
        var transform = RenderSystem.getDynamicUniforms().writeTransform(pose, tint, ZERO, IDENTITY);
        try (var pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "mmclutils-effects", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transform);
            pass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());
            pass.setVertexBuffer(0, verticesBuffer);
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(0, 0, indexCount, 1);
        }
    }

    @Override
    public void close() {
        if (verticesBuffer != null) {
            verticesBuffer.close();
            verticesBuffer = null;
        }
        buffer = 0;
        vertices = 0;
    }
}
