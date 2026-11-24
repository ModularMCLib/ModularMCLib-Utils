package com.modularmc.mmclutils.client.render.geometry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

/** 保存能跨帧使用的 GPU 顶点和索引。上传、绘制及释放都在渲染线程完成。 */
public final class StaticMesh implements AutoCloseable {

    private final int array;
    private final int vertices;
    private final int indices;
    private final MeshData.DrawState state;
    private boolean closed;

    private StaticMesh(int array, int vertices, int indices, MeshData.DrawState state) {
        this.array = array;
        this.vertices = vertices;
        this.indices = indices;
        this.state = state;
    }

    /** 复制网格数据。调用方仍负责关闭传入的网格。 */
    public static StaticMesh upload(String label, MeshData mesh) {
        RenderSystem.assertOnRenderThread();
        int previousArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        var indexBytes = mesh.indexBuffer();
        int array = GL30.glGenVertexArrays();
        int vertices = GL15.glGenBuffers();
        int indices = indexBytes == null ? 0 : GL15.glGenBuffers();
        var output = new StaticMesh(array, vertices, indices, mesh.drawState());
        try {
            if (array == 0 || vertices == 0 || indexBytes != null && indices == 0) {
                throw new IllegalStateException("无法为网格 " + label + " 分配缓冲");
            }
            GlStateManager._glBindVertexArray(array);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, vertices);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, mesh.vertexBuffer(), GL15.GL_STATIC_DRAW);
            output.format().setupBufferState();
            if (indices != 0) {
                GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, indices);
                GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBytes, GL15.GL_STATIC_DRAW);
            }
            return output;
        } catch (RuntimeException | Error failure) {
            output.close();
            throw failure;
        } finally {
            GlStateManager._glBindVertexArray(previousArray);
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
        }
    }

    /** 返回网格在上传时使用的顶点格式。 */
    public VertexFormat format() {
        return state.format();
    }

    /** 返回网格的图元模式，供调用方配置渲染状态。 */
    public VertexFormat.Mode mode() {
        return state.mode();
    }

    /** 用匹配的着色器绘制网格。混合、深度和贴图由调用方配置。 */
    public void draw(Matrix4f modelView, Matrix4f projection, ShaderInstance shader) {
        RenderSystem.assertOnRenderThread();
        if (closed) throw new IllegalStateException("网格已关闭");
        int previousArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        try {
            GlStateManager._glBindVertexArray(array);
            VertexFormat.IndexType indexType = state.indexType();
            if (indices == 0) {
                var sequential = RenderSystem.getSequentialBuffer(state.mode());
                sequential.bind(state.indexCount());
                indexType = sequential.type();
            }
            shader.setDefaultUniforms(state.mode(), modelView, projection, Minecraft.getInstance().getWindow());
            shader.apply();
            RenderSystem.drawElements(state.mode().asGLMode, state.indexCount(), indexType.asGLType);
        } finally {
            shader.clear();
            GlStateManager._glBindVertexArray(previousArray);
        }
    }

    /** 在渲染线程释放网格的顶点和索引。 */
    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (closed) return;
        GlStateManager._glDeleteVertexArrays(array);
        GlStateManager._glDeleteBuffers(vertices);
        if (indices != 0) GlStateManager._glDeleteBuffers(indices);
        closed = true;
    }
}
