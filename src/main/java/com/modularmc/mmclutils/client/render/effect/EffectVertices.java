package com.modularmc.mmclutils.client.render.effect;

import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/** 按变换反馈的排列写入四边形，供没有 OpenGL 反馈能力的设备使用。 */
final class EffectVertices {

    private static final float[] X = { 1, -1, -1, 1 };
    private static final float[] Y = { -1, -1, 1, 1 };
    private @Nullable ByteBuffer bytes;

    void begin(int quads) {
        int length = Math.multiplyExact(Math.max(1, quads), 4 * EffectQuadBuffer.VERTEX_BYTES);
        if (bytes == null) bytes = MemoryUtil.memAlloc(length);
        else if (bytes.capacity() < length) bytes = MemoryUtil.memRealloc(bytes, length);
        bytes.clear();
    }

    void facing(float x, float y, float z, Vector3fc left, Vector3fc up, float size, float angle,
                float red, float green, float blue, float alpha, float u0, float v0, float u1, float v1) {
        float cosine = (float) Math.cos(angle);
        float sine = (float) Math.sin(angle);
        float half = size * 0.5f;
        for (int vertex = 0; vertex < 4; vertex++) {
            float horizontal = (X[vertex] * cosine - Y[vertex] * sine) * half;
            float vertical = (X[vertex] * sine + Y[vertex] * cosine) * half;
            bytes.putFloat(x + left.x() * horizontal + up.x() * vertical);
            bytes.putFloat(y + left.y() * horizontal + up.y() * vertical);
            bytes.putFloat(z + left.z() * horizontal + up.z() * vertical);
            bytes.putFloat(red).putFloat(green).putFloat(blue).putFloat(alpha);
            bytes.putFloat(X[vertex] > 0 ? u0 : u1).putFloat(Y[vertex] > 0 ? v0 : v1);
        }
    }

    void writeVertex(float x, float y, float z, int rgb, float alpha, float u, float v) {
        writeVertex(x, y, z, (rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, alpha, u, v);
    }

    void writeVertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v) {
        bytes.putFloat(x).putFloat(y).putFloat(z);
        bytes.putFloat(red).putFloat(green).putFloat(blue).putFloat(alpha).putFloat(u).putFloat(v);
    }

    void upload(EffectQuadBuffer output) {
        output.upload(bytes.flip());
    }

    void release() {
        if (bytes != null) {
            MemoryUtil.memFree(bytes);
            bytes = null;
        }
    }
}
