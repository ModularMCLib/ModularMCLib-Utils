package com.modularmc.mmclutils.client.render.geometry;

import com.modularmc.mmclutils.api.schedule.BoundedVirtualTaskQueue;
import com.modularmc.mmclutils.api.shape.VoxelShapeGeometry;

import net.minecraft.core.Direction;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;

/** 从已采集的区段生成局部坐标的面与轮廓。计算期间不持有世界或 GPU 对象。 */
public final class SectionGeometryRenderer {

    private static final int MAX_VERTICES = 32768;

    private SectionGeometryRenderer() {}

    public static BoundedVirtualTaskQueue<Batch> queue(int parallelism, int resultCapacity) {
        return new BoundedVirtualTaskQueue<>("mmclutils-section-", parallelism, resultCapacity, Batch::close);
    }

    /** 在队列所属线程提交。键可用原版区段坐标；同一个键的旧任务由调用方作废。 */
    public static void submit(BoundedVirtualTaskQueue<Batch> queue, long key, SectionGeometryData data, Style style) {
        queue.submit(key, work -> build(data, style, work::cancelled, work::emit));
    }

    /** 每个已交付批次的关闭责任转交给回调。色值的透明分量为零时跳过对应图元。 */
    public static void build(SectionGeometryData data, Style style, BooleanSupplier cancelled, Consumer<Batch> output) {
        VoxelContours contours = (style.outlineColor() >>> 24) == 0 ? null : new VoxelContours(data, cancelled);
        try (var surfaces = new Buffer(data.origin(), VertexFormat.Mode.QUADS, output)) {
            for (int cell = 0; cell < 4096; cell++) {
                checkCancelled(cancelled);
                int x = cell & 15, y = cell >>> 8, z = cell >>> 4 & 15;
                var shape = data.shape(x, y, z);
                if (shape.empty()) continue;
                int color = style.surfaceColor().applyAsInt(data.temperatures()[cell]);
                if ((color >>> 24) == 0 && contours == null) continue;
                emitBlock(surfaces, contours, shape, x, y, z, data.visibleFaces()[cell], color, cancelled);
            }
            surfaces.flush();
        }
        if (contours == null) return;
        try (var outline = new Buffer(data.origin(), VertexFormat.Mode.DEBUG_LINES, output)) {
            contours.write(edge -> {
                checkCancelled(cancelled);
                outline.vertex(edge.x1(), edge.y1(), edge.z1(), style.outlineColor());
                outline.vertex(edge.x2(), edge.y2(), edge.z2(), style.outlineColor());
                outline.flushIfFull();
            });
            outline.flush();
        }
    }

    private static void emitBlock(Buffer surfaces, @Nullable VoxelContours contours, VoxelShapeGeometry shape,
                                  int x, int y, int z, int visible, int color, BooleanSupplier cancelled) {
        double[] xs = shape.x(), ys = shape.y(), zs = shape.z();
        for (int i = 0; i < xs.length - 1; i++) {
            for (int j = 0; j < ys.length - 1; j++) {
                for (int k = 0; k < zs.length - 1; k++) {
                    if ((k & 63) == 0) checkCancelled(cancelled);
                    if (!shape.filled(i, j, k)) continue;
                    float a = (float) (x + xs[i]), b = (float) (y + ys[j]), c = (float) (z + zs[k]);
                    float d = (float) (x + xs[i + 1]), e = (float) (y + ys[j + 1]), f = (float) (z + zs[k + 1]);
                    if (!shape.filled(i - 1, j, k) && visible(visible, Direction.WEST, xs[i], 0)) face(surfaces, contours, x, y, z, color, a, b, c, a, b, f, a, e, f, a, e, c);
                    if (!shape.filled(i + 1, j, k) && visible(visible, Direction.EAST, xs[i + 1], 1)) face(surfaces, contours, x, y, z, color, d, b, f, d, b, c, d, e, c, d, e, f);
                    if (!shape.filled(i, j - 1, k) && visible(visible, Direction.DOWN, ys[j], 0)) face(surfaces, contours, x, y, z, color, a, b, c, d, b, c, d, b, f, a, b, f);
                    if (!shape.filled(i, j + 1, k) && visible(visible, Direction.UP, ys[j + 1], 1)) face(surfaces, contours, x, y, z, color, a, e, f, d, e, f, d, e, c, a, e, c);
                    if (!shape.filled(i, j, k - 1) && visible(visible, Direction.NORTH, zs[k], 0)) face(surfaces, contours, x, y, z, color, d, b, c, a, b, c, a, e, c, d, e, c);
                    if (!shape.filled(i, j, k + 1) && visible(visible, Direction.SOUTH, zs[k + 1], 1)) face(surfaces, contours, x, y, z, color, a, b, f, d, b, f, d, e, f, a, e, f);
                }
            }
        }
    }

    private static boolean visible(int mask, Direction direction, double position, int boundary) {
        return position != boundary || (mask & 1 << direction.ordinal()) != 0;
    }

    private static void face(Buffer buffer, @Nullable VoxelContours contours, int x, int y, int z, int color,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        if ((color >>> 24) != 0) {
            buffer.vertex(x1, y1, z1, color);
            buffer.vertex(x2, y2, z2, color);
            buffer.vertex(x3, y3, z3, color);
            buffer.vertex(x4, y4, z4, color);
            buffer.flushIfFull();
        }
        if (contours != null) {
            contours.add(x, y, z, x1, y1, z1, x2, y2, z2);
            contours.add(x, y, z, x2, y2, z2, x3, y3, z3);
            contours.add(x, y, z, x3, y3, z3, x4, y4, z4);
            contours.add(x, y, z, x4, y4, z4, x1, y1, z1);
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }

    /** 面着色函数接收采集的温度值，在计算线程调用。边框使用固定 ARGB。 */
    public record Style(IntUnaryOperator surfaceColor, int outlineColor) {

        public Style(int surfaceColor, int outlineColor) {
            this(ignored -> surfaceColor, outlineColor);
        }
    }

    /** 一批顶点和区段原点的打包坐标。可在任意线程关闭，在渲染线程上传。 */
    public record Batch(long origin, MeshData mesh, ByteBufferBuilder memory) implements AutoCloseable {

        public StaticMesh upload(String label) {
            return StaticMesh.upload(label, mesh);
        }

        @Override
        public void close() {
            try {
                mesh.close();
            } finally {
                memory.close();
            }
        }
    }

    private static final class Buffer implements AutoCloseable {

        private final long origin;
        private final VertexFormat.Mode mode;
        private final Consumer<Batch> output;
        private @Nullable ByteBufferBuilder memory;
        private @Nullable BufferBuilder builder;
        private int vertices;

        private Buffer(long origin, VertexFormat.Mode mode, Consumer<Batch> output) {
            this.origin = origin;
            this.mode = mode;
            this.output = output;
        }

        private void vertex(float x, float y, float z, int color) {
            if (builder == null) {
                memory = new ByteBufferBuilder(4096);
                builder = new BufferBuilder(memory, mode, DefaultVertexFormat.POSITION_COLOR);
            }
            builder.addVertex(x, y, z).setColor(color);
            vertices++;
        }

        private void flushIfFull() {
            if (vertices >= MAX_VERTICES) flush();
        }

        private void flush() {
            if (builder == null) return;
            var batch = new Batch(origin, builder.buildOrThrow(), memory);
            builder = null;
            memory = null;
            vertices = 0;
            output.accept(batch);
        }

        @Override
        public void close() {
            if (memory != null) memory.close();
        }
    }
}
