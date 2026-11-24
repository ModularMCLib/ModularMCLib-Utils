package com.modularmc.mmclutils.client.render.geometry;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.jspecify.annotations.Nullable;

/** 保存能跨帧使用的 GPU 顶点和索引。上传、提交及释放都在渲染线程完成。 */
public final class StaticMesh implements AutoCloseable {

    private final GpuBuffer vertices;
    private final @Nullable GpuBuffer indices;
    private final MeshData.DrawState state;

    private StaticMesh(GpuBuffer vertices, @Nullable GpuBuffer indices, MeshData.DrawState state) {
        this.vertices = vertices;
        this.indices = indices;
        this.state = state;
    }

    /** 复制网格数据。调用方仍负责关闭传入的网格。 */
    public static StaticMesh upload(String label, MeshData mesh) {
        RenderSystem.assertOnRenderThread();
        var device = RenderSystem.getDevice();
        GpuBuffer vertices = device.createBuffer(() -> label + " vertices", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
        try {
            var bytes = mesh.indexBuffer();
            GpuBuffer indices = bytes == null ? null : device.createBuffer(() -> label + " indices", GpuBuffer.USAGE_INDEX, bytes);
            return new StaticMesh(vertices, indices, mesh.drawState());
        } catch (RuntimeException | Error failure) {
            vertices.close();
            throw failure;
        }
    }

    /** 返回网格在上传时使用的顶点格式。 */
    public VertexFormat format() {
        return state.format();
    }

    /** 返回网格的图元模式，供调用方选择绘制管线。 */
    public VertexFormat.Mode mode() {
        return state.mode();
    }

    /** 在已设置管线、变换及贴图的 pass 中提交。管线的顶点格式和图元须与网格一致。 */
    public void draw(RenderPass pass) {
        pass.setVertexBuffer(0, vertices);
        if (indices == null) {
            var sequential = RenderSystem.getSequentialBuffer(state.mode());
            pass.setIndexBuffer(sequential.getBuffer(state.indexCount()), sequential.type());
        } else {
            pass.setIndexBuffer(indices, state.indexType());
        }
        pass.drawIndexed(0, 0, state.indexCount(), 1);
    }

    /** 在渲染线程释放网格的顶点缓冲和独立索引缓冲。 */
    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        try {
            if (indices != null) indices.close();
        } finally {
            vertices.close();
        }
    }
}
