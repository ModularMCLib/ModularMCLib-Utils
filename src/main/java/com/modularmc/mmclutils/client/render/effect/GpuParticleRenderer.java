package com.modularmc.mmclutils.client.render.effect;

import com.modularmc.mmclutils.ModularMCLibUtils;
import com.modularmc.mmclutils.client.render.effect.ParticleEffects.MoteLook;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.TextureAtlas;

import com.mojang.blaze3d.opengl.GlStateManager;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * 光点产生时写入槽位池。每帧通过变换反馈批量展开为朝向相机的四边形，再用原版管线绘制。
 */
final class GpuParticleRenderer {

    static final int BALLISTIC = 0, SLIDE = 1, PULSE = 2, BURST = 3, FLOW = 4;
    static final int FADE = 1, ANIMATED = 4;
    static final int SHRINK = 0, PULSE_SIZE = 1, CONSTANT = 2;
    static final float CULL = (float) ParticleEffects.SPAWN_DISTANCE + 8;
    private static final int RECORD_BYTES = 128;
    private static final int FRAME_COLUMNS = 64;
    private static final int FRAME_ROWS = 64;
    private static final int PROBES = 64;

    static final class Pool {

        final boolean atlas, throughWalls, stream;
        final int capacity;
        final ByteBuffer mirror;
        final long address;
        final float[] ends;
        int cursor, highWater, dirtyMin = Integer.MAX_VALUE, dirtyMax = -1, count;
        double maxEnd;
        int buffer = -1;
        final EffectQuadBuffer out = new EffectQuadBuffer();
        final EffectQuadBuffer coreOut = new EffectQuadBuffer();

        Pool(int capacity, boolean atlas, boolean throughWalls, boolean stream) {
            this.capacity = capacity;
            this.atlas = atlas;
            this.throughWalls = throughWalls;
            this.stream = stream;
            this.mirror = MemoryUtil.memAlloc(capacity * RECORD_BYTES);
            this.address = MemoryUtil.memAddress(mirror);
            this.ends = new float[capacity];
        }

        long slot(double now, double end) {
            int slot;
            if (stream) {
                if (count >= capacity) return -1;
                slot = count++;
            } else {
                slot = -1;
                for (int i = 0; i < PROBES && highWater > 0; i++) {
                    if (cursor >= highWater) cursor = 0;
                    int s = cursor++;
                    if (ends[s] <= now) {
                        slot = s;
                        break;
                    }
                }
                if (slot < 0) {
                    if (highWater < capacity) {
                        slot = highWater;
                    } else {
                        if (cursor >= capacity) cursor = 0;
                        slot = cursor++;
                    }
                }
                ends[slot] = (float) end;
                maxEnd = Math.max(maxEnd, end);
                dirtyMin = Math.min(dirtyMin, slot);
                dirtyMax = Math.max(dirtyMax, slot);
            }
            highWater = Math.max(highWater, slot + 1);
            return address + (long) slot * RECORD_BYTES;
        }

        void trim(double now) {
            while (highWater > 0 && ends[highWater - 1] <= now) highWater--;
        }

        void clear() {
            cursor = highWater = count = 0;
            maxEnd = 0;
            dirtyMin = Integer.MAX_VALUE;
            dirtyMax = -1;
            Arrays.fill(ends, 0);
        }
    }

    private static final Pool GLOW = new Pool(16384, false, false, false);
    private static final Pool GLOW_X = new Pool(4096, false, true, false);
    private static final Pool ATLAS = new Pool(32768, true, false, false);
    private static final Pool ATLAS_X = new Pool(8192, true, true, false);
    private static final Pool FLOW_GLOW = new Pool(4096, false, false, true);
    private static final Pool FLOW_ATLAS = new Pool(4096, true, false, true);
    private static final Pool[] POOLS = { GLOW, GLOW_X, ATLAS, ATLAS_X, FLOW_GLOW, FLOW_ATLAS };
    private static final SplittableRandom RANDOM = new SplittableRandom(0x6D2B79F5L);
    private static final ByteBuffer FRAME_PIXELS = MemoryUtil.memAlloc(FRAME_COLUMNS * FRAME_ROWS * 16);
    private static TransformFeedbackProgram program;
    private static int programState = -1;
    private static int simulateVao = -1;
    private static int frameTexture = -1;
    private static int frameRows;
    private static int frameGeneration = -1;
    private static boolean framesDirty;
    private static double epoch = Double.NaN;
    private static int lastDrawn;

    private GpuParticleRenderer() {}

    static boolean available() {
        if (programState < 0) {
            boolean ok = RingRenderer.available();
            if (ok) program = TransformFeedbackProgram.load("mote.vsh");
            programState = ok && program != null ? 1 : 0;
            if (programState == 0) ModularMCLibUtils.LOGGER.info("光点采用普通顶点绘制");
        }
        return programState == 1;
    }

    static float rel(double time) {
        if (Double.isNaN(epoch)) epoch = Math.floor(time);
        return (float) (time - epoch);
    }

    static void clear() {
        for (var pool : POOLS) pool.clear();
        epoch = Double.NaN;
    }

    static void beginFrame() {
        FLOW_GLOW.clear();
        FLOW_ATLAS.clear();
    }

    static int lastDrawn() {
        return lastDrawn;
    }

    static int live(double now) {
        int n = 0;
        for (var pool : POOLS) {
            if (pool.stream) {
                n += pool.count;
                continue;
            }
            for (int i = 0; i < pool.highWater; i++) {
                if (pool.ends[i] > now) n++;
            }
        }
        return n;
    }

    private static Pool pool(MoteLook look, boolean throughWalls, boolean stream) {
        boolean atlas = look.sprite() != null;
        if (stream) return atlas ? FLOW_ATLAS : FLOW_GLOW;
        if (atlas) return throughWalls ? ATLAS_X : ATLAS;
        return throughWalls ? GLOW_X : GLOW;
    }

    private static int frameRow(MoteSprite sprite) {
        if (frameGeneration != MoteSprite.generation) {
            frameGeneration = MoteSprite.generation;
            frameRows = 0;
        }
        if (sprite.tableRow >= 0 && sprite.tableRow < frameRows) return sprite.tableRow;
        if (frameRows >= FRAME_ROWS) return -1;
        float[] uvs = sprite.uvs();
        int row = frameRows++;
        int frames = Math.min(FRAME_COLUMNS, uvs.length / 4);
        long base = MemoryUtil.memAddress(FRAME_PIXELS) + (long) row * FRAME_COLUMNS * 16;
        for (int i = 0; i < frames * 4; i++) MemoryUtil.memPutFloat(base + i * 4L, uvs[i]);
        sprite.tableRow = row;
        framesDirty = true;
        return row;
    }

    static long write(int motion, double x, double y, double z, double ax, double ay, double az, double start, float life, float drag, float gravity,
                      float size, float alpha, int rgb, int curve, int flags, float roll, float rollSpeed, float e0, float e1, float e2, float e3,
                      MoteLook look, boolean throughWalls) {
        boolean stream = motion == FLOW;
        var pool = pool(look, throughWalls, stream);
        float rectA = 0, rectB = 0, rectC = 1, rectD = 1;
        var sprite = look.sprite();
        if (sprite != null) {
            int frames = sprite.frames();
            if (frames == 0) return -1;
            if (look.animated() && frames > 1) {
                int row = frameRow(sprite);
                if (row < 0) return -1;
                flags |= ANIMATED;
                rectA = row;
                rectB = frames;
            } else {
                float[] uvs = sprite.uvs();
                int f = frames == 1 ? 0 : RANDOM.nextInt(frames);
                rectA = uvs[f * 4];
                rectB = uvs[f * 4 + 1];
                rectC = uvs[f * 4 + 2];
                rectD = uvs[f * 4 + 3];
            }
        }
        long a = pool.slot(start, start + life);
        if (a < 0) return -1;
        double hx = Math.floor(x / 1024) * 1024, hy = Math.floor(y / 1024) * 1024, hz = Math.floor(z / 1024) * 1024;
        MemoryUtil.memPutFloat(a, (float) (x - hx));
        MemoryUtil.memPutFloat(a + 4, (float) (y - hy));
        MemoryUtil.memPutFloat(a + 8, (float) (z - hz));
        MemoryUtil.memPutFloat(a + 12, stream ? 0 : rel(start));
        MemoryUtil.memPutFloat(a + 16, (float) hx);
        MemoryUtil.memPutFloat(a + 20, (float) hy);
        MemoryUtil.memPutFloat(a + 24, (float) hz);
        MemoryUtil.memPutFloat(a + 28, life);
        MemoryUtil.memPutFloat(a + 32, (float) ax);
        MemoryUtil.memPutFloat(a + 36, (float) ay);
        MemoryUtil.memPutFloat(a + 40, (float) az);
        MemoryUtil.memPutFloat(a + 44, motion);
        MemoryUtil.memPutFloat(a + 48, drag);
        MemoryUtil.memPutFloat(a + 52, gravity);
        MemoryUtil.memPutFloat(a + 56, size);
        MemoryUtil.memPutFloat(a + 60, alpha);
        MemoryUtil.memPutFloat(a + 64, (rgb >> 16 & 0xFF) / 255F);
        MemoryUtil.memPutFloat(a + 68, (rgb >> 8 & 0xFF) / 255F);
        MemoryUtil.memPutFloat(a + 72, (rgb & 0xFF) / 255F);
        MemoryUtil.memPutFloat(a + 76, sprite == null ? look.core() : 0);
        MemoryUtil.memPutFloat(a + 80, rectA);
        MemoryUtil.memPutFloat(a + 84, rectB);
        MemoryUtil.memPutFloat(a + 88, rectC);
        MemoryUtil.memPutFloat(a + 92, rectD);
        MemoryUtil.memPutFloat(a + 96, curve);
        MemoryUtil.memPutFloat(a + 100, flags);
        MemoryUtil.memPutFloat(a + 104, roll);
        MemoryUtil.memPutFloat(a + 108, rollSpeed);
        MemoryUtil.memPutFloat(a + 112, e0);
        MemoryUtil.memPutFloat(a + 116, e1);
        MemoryUtil.memPutFloat(a + 120, e2);
        MemoryUtil.memPutFloat(a + 124, e3);
        return a;
    }

    private static void upload(Pool pool) {
        if (pool.buffer == -1) {
            pool.buffer = GlStateManager._glGenBuffers();
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, pool.buffer);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, (long) pool.capacity * RECORD_BYTES, GL15.GL_DYNAMIC_DRAW);
        } else {
            GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, pool.buffer);
        }
        int from, to;
        if (pool.stream) {
            from = 0;
            to = pool.count - 1;
        } else {
            from = pool.dirtyMin;
            to = pool.dirtyMax;
            pool.dirtyMin = Integer.MAX_VALUE;
            pool.dirtyMax = -1;
        }
        if (to < from) return;
        pool.mirror.position(from * RECORD_BYTES);
        pool.mirror.limit((to + 1) * RECORD_BYTES);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, (long) from * RECORD_BYTES, pool.mirror);
        pool.mirror.clear();
    }

    private static void uploadFrames() {
        if (frameTexture == -1) {
            frameTexture = GlStateManager._genTexture();
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(frameTexture);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            framesDirty = true;
        }
        if (!framesDirty) return;
        framesDirty = false;
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(frameTexture);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        FRAME_PIXELS.clear();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, FRAME_COLUMNS, FRAME_ROWS, 0, GL11.GL_RGBA, GL11.GL_FLOAT, FRAME_PIXELS);
    }

    private static boolean active(Pool pool, double now) {
        if (pool.stream) return pool.count > 0;
        if (pool.highWater == 0) return false;
        if (pool.maxEnd <= now) {
            pool.clear();
            return false;
        }
        pool.trim(now);
        return pool.highWater > 0;
    }

    private static void capture(EffectQuadBuffer output, int instances) {
        int vertices = instances * 4;
        output.reserve(vertices);
        GL30.glBindBufferRange(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, output.buffer, 0, (long) vertices * EffectQuadBuffer.VERTEX_BYTES);
        GL30.glBeginTransformFeedback(GL11.GL_POINTS);
        GL31.glDrawArraysInstanced(GL11.GL_POINTS, 0, 4, instances);
        GL30.glEndTransformFeedback();
        output.vertices = vertices;
    }

    private static void pointPool(Pool pool) {
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, pool.buffer);
        for (int i = 0; i < program.ring.length; i++) {
            int location = program.ring[i];
            if (location < 0) continue;
            GL20.glEnableVertexAttribArray(location);
            GL20.glVertexAttribPointer(location, 4, GL11.GL_FLOAT, false, RECORD_BYTES, i * 16L);
            GL33.glVertexAttribDivisor(location, 1);
        }
    }

    static void flush(double frameTime, Camera camera) {
        double now = frameTime;
        boolean any = false;
        for (var pool : POOLS) any |= active(pool, now);
        if (!any) {
            lastDrawn = 0;
            return;
        }
        if (!available()) {
            lastDrawn = 0;
            for (var pool : POOLS) {
                if (!active(pool, now)) continue;
                ParticleGeometry.generate(pool, now, epoch, camera, FRAME_PIXELS, FRAME_COLUMNS);
                present(pool);
                lastDrawn += pool.out.vertices / 4;
            }
            return;
        }
        var cam = camera.position();
        double hx = Math.floor(cam.x / 1024) * 1024, hy = Math.floor(cam.y / 1024) * 1024, hz = Math.floor(cam.z / 1024) * 1024;
        var left = new Vector3f(-1, 0, 0).rotate(camera.rotation());
        var up = new Vector3f(0, 1, 0).rotate(camera.rotation());
        uploadFrames();
        if (simulateVao == -1) simulateVao = GlStateManager._glGenVertexArrays();
        GlStateManager._glBindVertexArray(simulateVao);
        GlStateManager._glUseProgram(program.id);
        GL20.glUniform1f(program.uniform("Now"), Double.isNaN(epoch) ? 0 : (float) (now - epoch));
        GL20.glUniform3f(program.uniform("CamHi"), (float) hx, (float) hy, (float) hz);
        GL20.glUniform3f(program.uniform("CamLo"), (float) (cam.x - hx), (float) (cam.y - hy), (float) (cam.z - hz));
        GL20.glUniform3f(program.uniform("CameraLeft"), left.x(), left.y(), left.z());
        GL20.glUniform3f(program.uniform("CameraUp"), up.x(), up.y(), up.z());
        GL20.glUniform1f(program.uniform("CullDistance"), CULL);
        GL20.glUniform1i(program.uniform("SpriteFrames"), 0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(frameTexture);
        int corePass = program.uniform("CorePass");
        int premultiply = program.uniform("Premultiply");
        GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
        int drawn = 0;
        for (var pool : POOLS) {
            pool.out.vertices = 0;
            pool.coreOut.vertices = 0;
            if (!active(pool, now)) continue;
            upload(pool);
            int instances = pool.stream ? pool.count : pool.highWater;
            drawn += instances;
            pointPool(pool);
            GL20.glUniform1i(premultiply, pool.atlas ? 0 : 1);
            GL20.glUniform1i(corePass, 0);
            capture(pool.out, instances);
            if (!pool.atlas) {
                GL20.glUniform1i(corePass, 1);
                capture(pool.coreOut, instances);
            }
        }
        GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, 0);
        GlStateManager._glUseProgram(0);
        GlStateManager._glBindVertexArray(0);
        lastDrawn = drawn;
        for (var pool : POOLS) present(pool);
    }

    private static void present(Pool pool) {
        if (pool.atlas) {
            pool.out.draw(EffectRenderPipelines.atlas(pool.throughWalls), TextureAtlas.LOCATION_PARTICLES);
        } else {
            pool.out.draw(EffectRenderPipelines.glow(pool.throughWalls), EffectRenderPipelines.MOTE_TEXTURE);
            pool.coreOut.draw(EffectRenderPipelines.glow(pool.throughWalls), EffectRenderPipelines.WHITE_TEXTURE);
        }
    }

    static void reload() {
        for (var pool : POOLS) {
            pool.out.close();
            pool.coreOut.close();
            if (pool.buffer != -1) GlStateManager._glDeleteBuffers(pool.buffer);
            pool.buffer = -1;
        }
        if (frameTexture != -1) GlStateManager._deleteTexture(frameTexture);
        if (simulateVao != -1) GL30.glDeleteVertexArrays(simulateVao);
        if (program != null) GL20.glDeleteProgram(program.id);
        frameTexture = simulateVao = programState = -1;
        program = null;
        ParticleGeometry.release();
        clear();
    }
}
