package com.modularmc.mmclutils.client.render.texture;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** 以最近访问顺序缓存动态贴图。每个缓存须独占其注册标识，清理时释放对应 GPU 资源。 */
public final class ImageTextures implements AutoCloseable {

    private final TextureManager textures;
    private final int capacity;
    private final Object2ObjectLinkedOpenHashMap<Identifier, DynamicTexture> entries = new Object2ObjectLinkedOpenHashMap<>();

    public ImageTextures(TextureManager textures, int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("贴图缓存容量必须为正数");
        this.textures = textures;
        this.capacity = capacity;
    }

    public @Nullable DynamicTexture get(Identifier id) {
        RenderSystem.assertOnRenderThread();
        return entries.getAndMoveToLast(id);
    }

    public DynamicTexture load(Identifier id, Path path, Limits limits) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return load(id, input, limits);
        }
    }

    /** 在渲染线程读取和上传。输入流由调用方关闭。 */
    public DynamicTexture load(Identifier id, InputStream input, Limits limits) throws IOException {
        RenderSystem.assertOnRenderThread();
        return load(id, input.readNBytes(limits.bytes() + 1), limits);
    }

    /** 读取 PNG 数据。解码前限制文件大小和像素数。 */
    public DynamicTexture load(Identifier id, byte[] bytes, Limits limits) throws IOException {
        RenderSystem.assertOnRenderThread();
        if (bytes.length > limits.bytes()) throw new IOException("贴图数据超出字节上限 " + id);
        checkHeader(id, bytes, limits);
        NativeImage image = NativeImage.read(bytes);
        DynamicTexture texture;
        try {
            texture = new DynamicTexture(id::toString, image);
        } catch (RuntimeException | Error failure) {
            image.close();
            throw failure;
        }
        try {
            textures.register(id, texture);
        } catch (RuntimeException | Error failure) {
            texture.close();
            throw failure;
        }
        entries.putAndMoveToLast(id, texture);
        if (entries.size() > capacity) invalidate(entries.firstKey());
        return texture;
    }

    public void invalidate(Identifier id) {
        RenderSystem.assertOnRenderThread();
        if (entries.remove(id) != null) textures.release(id);
    }

    public void clear() {
        RenderSystem.assertOnRenderThread();
        while (!entries.isEmpty()) invalidate(entries.firstKey());
    }

    @Override
    public void close() {
        clear();
    }

    private static void checkHeader(Identifier id, byte[] bytes, Limits limits) throws IOException {
        if (bytes.length < 33 || readInt(bytes, 0) != 0x89504e47 || readInt(bytes, 4) != 0x0d0a1a0a || readInt(bytes, 8) != 13 || readInt(bytes, 12) != 0x49484452) {
            throw new IOException("贴图缺少有效的 PNG 标头 " + id);
        }
        int width = readInt(bytes, 16), height = readInt(bytes, 20);
        if (width < 1 || height < 1 || width > limits.dimension() || height > limits.dimension() || (long) width * height > limits.pixels()) {
            throw new IOException("贴图尺寸超出上限 " + id + " " + width + "×" + height);
        }
    }

    private static int readInt(byte[] data, int offset) {
        return (data[offset] & 255) << 24 | (data[offset + 1] & 255) << 16 | (data[offset + 2] & 255) << 8 | data[offset + 3] & 255;
    }

    /** 分别限制压缩数据、单边像素和总像素数。 */
    public record Limits(int bytes, int dimension, long pixels) {

        public Limits {
            if (bytes < 1 || bytes == Integer.MAX_VALUE || dimension < 1 || pixels < 1) {
                throw new IllegalArgumentException("贴图读取上限超出范围");
            }
        }
    }

    /** 限制等待中的请求数、单张图片的分块数、单块大小与组装总大小。 */
    public record RequestLimits(int requests, int parts, int partBytes, int imageBytes, Duration timeout) {

        public RequestLimits {
            if (requests < 1 || parts < 1 || partBytes < 1 || imageBytes < 1 || timeout.toNanos() <= 0) {
                throw new IllegalArgumentException("图片请求的容量与超时必须为正数");
            }
        }
    }

    /**
     * 按调用方的键组装乱序图片分块。各入口可由不同线程调用，完成后由调用方负责上传。
     * 同一个键重新发起时分配新的编号，迟到的旧数据随即失效。
     */
    public static final class Requests<K> {

        private final RequestLimits limits;
        private final long timeout;
        private final Object2ObjectOpenHashMap<K, PendingRequest> pending = new Object2ObjectOpenHashMap<>();
        private long nextId;

        public Requests(RequestLimits limits) {
            this.limits = limits;
            timeout = limits.timeout().toNanos();
        }

        /** 新请求返回正编号。相同的键正在接收或已经达到并发上限时返回零。 */
        public synchronized long start(K key) {
            return start(key, false);
        }

        /** 替换同一个键的旧请求。达到并发上限且没有可替换的请求时返回零。 */
        public synchronized long restart(K key) {
            return start(key, true);
        }

        private long start(K key, boolean replace) {
            long now = System.nanoTime();
            expire(now);
            if (pending.containsKey(key) ? !replace : pending.size() >= limits.requests()) return 0;
            nextId = Math.incrementExact(nextId);
            pending.put(key, new PendingRequest(nextId, now + timeout));
            return nextId;
        }

        /** 接收零基编号的数据。接收器复制获准的分块，调用方可在方法返回后复用原数组。 */
        public synchronized ReceiveResult receive(K key, long id, int index, int total, byte[] data) {
            PendingRequest request = find(key, id);
            if (request == null) return new ReceiveResult(ReceiveStatus.IGNORED, null);
            if (index < 0 || index >= total || total > limits.parts() || data.length > limits.partBytes() ||
                    (request.parts != null && request.parts.length != total)) {
                pending.remove(key);
                return new ReceiveResult(ReceiveStatus.REJECTED, null);
            }
            if (request.parts == null) request.parts = new byte[total][];
            if (request.parts[index] != null) return new ReceiveResult(ReceiveStatus.DUPLICATE, null);
            if (data.length > limits.imageBytes() - request.bytes) {
                pending.remove(key);
                return new ReceiveResult(ReceiveStatus.REJECTED, null);
            }
            request.parts[index] = data.clone();
            request.bytes += data.length;
            if (++request.received != total) return new ReceiveResult(ReceiveStatus.WAITING, null);

            pending.remove(key);
            byte[] image = new byte[request.bytes];
            int offset = 0;
            for (byte[] part : request.parts) {
                System.arraycopy(part, 0, image, offset, part.length);
                offset += part.length;
            }
            return new ReceiveResult(ReceiveStatus.COMPLETE, image);
        }

        /** 完成一项已匹配的失败响应。过期请求和旧编号按迟到数据处理。 */
        public synchronized ReceiveResult fail(K key, long id) {
            if (!cancel(key, id)) return new ReceiveResult(ReceiveStatus.IGNORED, null);
            return new ReceiveResult(ReceiveStatus.FAILED, null);
        }

        public synchronized boolean cancel(K key, long id) {
            return find(key, id) != null && pending.remove(key) != null;
        }

        public synchronized void expire() {
            expire(System.nanoTime());
        }

        public synchronized void clear() {
            pending.clear();
        }

        private void expire(long now) {
            pending.values().removeIf(request -> now - request.deadline >= 0);
        }

        private @Nullable PendingRequest find(K key, long id) {
            PendingRequest request = pending.get(key);
            if (request == null || request.id != id) return null;
            if (System.nanoTime() - request.deadline < 0) return request;
            pending.remove(key);
            return null;
        }

        @RequiredArgsConstructor
        private static final class PendingRequest {

            private final long id;
            private final long deadline;
            private byte @Nullable [][] parts;
            private int bytes;
            private int received;
        }
    }

    public enum ReceiveStatus {
        WAITING,
        DUPLICATE,
        COMPLETE,
        FAILED,
        REJECTED,
        IGNORED
    }

    /** COMPLETE 时交付完整的字节数组，其余状态的图片数据为空。 */
    public record ReceiveResult(ReceiveStatus status, byte @Nullable [] image) {}
}
