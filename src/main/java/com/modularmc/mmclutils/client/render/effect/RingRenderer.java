package com.modularmc.mmclutils.client.render.effect;

import com.modularmc.mmclutils.ModularMCLibUtils;

import net.minecraft.client.Camera;

import com.mojang.blaze3d.platform.GlStateManager;
import it.unimi.dsi.fastutil.objects.Reference2IntLinkedOpenHashMap;
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
import java.util.List;

final class RingRenderer {

    static final int RIBBON_SEGMENTS = 192;
    private static final int RECORD_BYTES = 112;
    private static final int VOXEL_VERTICES = 12;
    private static final int QUAD_VERTICES = 4;
    private static final int PALETTE_ROWS = 64;
    private static final int MAX_GROUPS = 16;
    private static final int DATA_TEXTURE_LIMIT = 64;
    private static final Reference2IntLinkedOpenHashMap<ParticleRing> DATA_TEXTURES = new Reference2IntLinkedOpenHashMap<>();
    private static final ParticleRing[] GROUP_RING = new ParticleRing[MAX_GROUPS];
    private static final int[] GROUP_FIRST = new int[MAX_GROUPS];
    private static final int[] GROUP_SIZE = new int[MAX_GROUPS];
    private static final int[] GROUP_TEXTURE = new int[MAX_GROUPS];
    private static final int[][] PALETTES = new int[PALETTE_ROWS][];
    private static final ByteBuffer PALETTE_PIXELS = MemoryUtil.memAlloc(EffectPalette.SIZE * PALETTE_ROWS * 4);
    private static int state = -1;
    private static TransformFeedbackProgram voxelProgram;
    private static TransformFeedbackProgram glowProgram;
    private static TransformFeedbackProgram ribbonProgram;
    private static int groups;
    private static int paletteRows;
    private static int records;
    private static int[] commandGroup = new int[64];
    private static int[] commandRow = new int[64];
    private static ByteBuffer table = MemoryUtil.memAlloc(64 * RECORD_BYTES);
    private static int tableBuffer = -1;
    private static int paletteTexture = -1;
    private static int simulateVao = -1;
    private static final EffectQuadBuffer VOXELS = new EffectQuadBuffer();
    private static final EffectQuadBuffer GLOWS = new EffectQuadBuffer();

    private RingRenderer() {}

    static boolean available() {
        if (state < 0) {
            boolean supported = TransformFeedbackProgram.supported();
            if (supported) {
                voxelProgram = TransformFeedbackProgram.load("ring_voxel.vsh");
                glowProgram = TransformFeedbackProgram.load("ring_glow.vsh");
                ribbonProgram = TransformFeedbackProgram.load("ring_ribbon.vsh");
            }
            state = supported && voxelProgram != null && glowProgram != null && ribbonProgram != null ? 1 : 0;
            if (state == 0) ModularMCLibUtils.LOGGER.info("环效果采用普通顶点绘制");
        }
        return state == 1;
    }

    private static void resetUnpack() {
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
    }

    private static int texture(int internalFormat, int width, int height, int type, ByteBuffer pixels) {
        int id = GlStateManager._genTexture();
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(id);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
        resetUnpack();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, GL11.GL_RGBA, type, pixels);
        return id;
    }

    private static int dataTexture(ParticleRing ring) {
        int cached = DATA_TEXTURES.getAndMoveToLast(ring);
        if (cached != 0) return cached;
        var data = ring.createData();
        try {
            int id = texture(GL30.GL_RGBA32F, ParticleRing.DATA_WIDTH, ring.dataRows(), GL11.GL_FLOAT, data);
            DATA_TEXTURES.putAndMoveToLast(ring, id);
            if (DATA_TEXTURES.size() > DATA_TEXTURE_LIMIT) GlStateManager._deleteTexture(DATA_TEXTURES.removeFirstInt());
            return id;
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    private static int paletteRow(int[] palette) {
        for (int i = 0; i < paletteRows; i++) {
            if (PALETTES[i] == palette) return i;
        }
        if (paletteRows == PALETTE_ROWS) return PALETTE_ROWS - 1;
        int row = paletteRows++;
        PALETTES[row] = palette;
        int base = row * EffectPalette.SIZE * 4;
        for (int i = 0; i < EffectPalette.SIZE; i++) {
            int rgb = palette[i];
            PALETTE_PIXELS.put(base + i * 4, (byte) (rgb >> 16));
            PALETTE_PIXELS.put(base + i * 4 + 1, (byte) (rgb >> 8));
            PALETTE_PIXELS.put(base + i * 4 + 2, (byte) rgb);
            PALETTE_PIXELS.put(base + i * 4 + 3, (byte) 0xFF);
        }
        return row;
    }

    private static void putColor(long address, int rgb, int strength) {
        MemoryUtil.memPutFloat(address, (rgb >> 16 & 0xFF) * strength / 65025F);
        MemoryUtil.memPutFloat(address + 4, (rgb >> 8 & 0xFF) * strength / 65025F);
        MemoryUtil.memPutFloat(address + 8, (rgb & 0xFF) * strength / 65025F);
        MemoryUtil.memPutFloat(address + 12, 0);
    }

    private static void buildTable(List<EffectRenderer.RingCommand> commands, int count) {
        groups = 0;
        paletteRows = 0;
        if (commandGroup.length < count) {
            commandGroup = new int[count * 2];
            commandRow = new int[count * 2];
        }
        for (int c = 0; c < count; c++) {
            var command = commands.get(c);
            int group = -1;
            for (int g = 0; g < groups; g++) {
                if (GROUP_RING[g] == command.ring) {
                    group = g;
                    break;
                }
            }
            if (group < 0) {
                group = groups < MAX_GROUPS ? groups++ : MAX_GROUPS - 1;
                GROUP_RING[group] = command.ring;
                GROUP_SIZE[group] = 0;
            }
            GROUP_SIZE[group]++;
            commandGroup[c] = group;
            commandRow[c] = paletteRow(command.palette);
        }
        int first = 0;
        for (int g = 0; g < groups; g++) {
            GROUP_FIRST[g] = first;
            first += GROUP_SIZE[g];
            GROUP_SIZE[g] = 0;
            var ring = GROUP_RING[g];
            GROUP_TEXTURE[g] = ring.count() == 0 ? 0 : dataTexture(ring);
        }
        records = count;
        if (table.capacity() < count * RECORD_BYTES) table = MemoryUtil.memRealloc(table, count * 2 * RECORD_BYTES);
        long base = MemoryUtil.memAddress(table);
        for (int c = 0; c < count; c++) {
            var command = commands.get(c);
            int g = commandGroup[c];
            int slot = GROUP_FIRST[g] + GROUP_SIZE[g]++;
            long address = base + (long) slot * RECORD_BYTES;
            var style = command.style;
            float breathe = EffectRenderer.breathe(command);
            MemoryUtil.memPutFloat(address, command.x);
            MemoryUtil.memPutFloat(address + 4, command.y);
            MemoryUtil.memPutFloat(address + 8, command.z);
            MemoryUtil.memPutFloat(address + 12, (float) command.time);
            MemoryUtil.memPutFloat(address + 16, command.ring.half() * command.scale);
            MemoryUtil.memPutFloat(address + 20, command.spriteSize);
            MemoryUtil.memPutFloat(address + 24, command.spriteStrength / 255F);
            MemoryUtil.memPutFloat(address + 28, commandRow[c]);
            MemoryUtil.memPutFloat(address + 32, command.axis.ordinal());
            MemoryUtil.memPutFloat(address + 36, command.ring.majorRadius());
            MemoryUtil.memPutFloat(address + 40, style.hazeWidth());
            MemoryUtil.memPutFloat(address + 44, style.coreWidth());
            putColor(address + 48, command.baseColor, (int) (style.hazeStrength() * command.intensity * breathe));
            putColor(address + 64, command.baseColor, (int) (style.coreStrength() * command.intensity * breathe));
            var shape = command.shape;
            MemoryUtil.memPutFloat(address + 80, shape.strands());
            MemoryUtil.memPutFloat(address + 84, shape.height());
            MemoryUtil.memPutFloat(address + 88, shape.turns());
            MemoryUtil.memPutFloat(address + 92, shape.taper());
            MemoryUtil.memPutFloat(address + 96, shape.fadeIn());
            MemoryUtil.memPutFloat(address + 100, shape.fadeOut());
            MemoryUtil.memPutFloat(address + 104, 0);
            MemoryUtil.memPutFloat(address + 108, 0);
        }
        if (tableBuffer == -1) tableBuffer = GlStateManager._glGenBuffers();
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, tableBuffer);
        table.position(0);
        table.limit(count * RECORD_BYTES);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, table, GL15.GL_STREAM_DRAW);
        table.clear();
        if (paletteTexture == -1) {
            PALETTE_PIXELS.clear();
            paletteTexture = texture(GL11.GL_RGBA8, EffectPalette.SIZE, PALETTE_ROWS, GL11.GL_UNSIGNED_BYTE, PALETTE_PIXELS);
        } else {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(paletteTexture);
            resetUnpack();
            PALETTE_PIXELS.position(0);
            PALETTE_PIXELS.limit(EffectPalette.SIZE * paletteRows * 4);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, EffectPalette.SIZE, paletteRows, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, PALETTE_PIXELS);
            PALETTE_PIXELS.clear();
        }
    }

    private static void pointRing(TransformFeedbackProgram program, int firstRecord, int divisor) {
        for (int i = 0; i < program.ring.length; i++) {
            int location = program.ring[i];
            if (location < 0) continue;
            GL20.glEnableVertexAttribArray(location);
            GL20.glVertexAttribPointer(location, 4, GL11.GL_FLOAT, false, RECORD_BYTES, (long) firstRecord * RECORD_BYTES + i * 16L);
            GL33.glVertexAttribDivisor(location, divisor);
        }
    }

    private static void useParticles(TransformFeedbackProgram program) {
        GlStateManager._glUseProgram(program.id);
        GL20.glUniform1i(program.uniform("ParticleData"), 0);
        GL20.glUniform1i(program.uniform("PaletteAtlas"), 1);
        GlStateManager._activeTexture(GL13.GL_TEXTURE1);
        GlStateManager._bindTexture(paletteTexture);
    }

    private static int capture(EffectQuadBuffer output, int offsetVertices, int vertices, int instances) {
        int count = vertices * instances;
        GL30.glBindBufferRange(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, output.buffer, (long) offsetVertices * EffectQuadBuffer.VERTEX_BYTES, (long) count * EffectQuadBuffer.VERTEX_BYTES);
        GL30.glBeginTransformFeedback(GL11.GL_POINTS);
        GL31.glDrawArraysInstanced(GL11.GL_POINTS, 0, vertices, instances);
        GL30.glEndTransformFeedback();
        return count;
    }

    private static void simulate(Camera camera) {
        int voxelVertices = 0, spriteVertices = 0;
        for (int g = 0; g < groups; g++) {
            int instances = GROUP_SIZE[g] * GROUP_RING[g].count();
            voxelVertices += instances * VOXEL_VERTICES;
            spriteVertices += instances * QUAD_VERTICES;
        }
        int ribbonVertices = records * RIBBON_SEGMENTS * 2 * QUAD_VERTICES;
        VOXELS.reserve(voxelVertices);
        GLOWS.reserve(spriteVertices + ribbonVertices);
        if (simulateVao == -1) simulateVao = GlStateManager._glGenVertexArrays();
        GlStateManager._glBindVertexArray(simulateVao);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, tableBuffer);
        GL11.glEnable(GL30.GL_RASTERIZER_DISCARD);
        int voxelOffset = 0, glowOffset = 0;
        useParticles(voxelProgram);
        int voxelCount = voxelProgram.uniform("ParticleCount");
        for (int g = 0; g < groups; g++) {
            var ring = GROUP_RING[g];
            if (ring.count() == 0) continue;
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(GROUP_TEXTURE[g]);
            GL20.glUniform1i(voxelCount, ring.count());
            pointRing(voxelProgram, GROUP_FIRST[g], ring.count());
            voxelOffset += capture(VOXELS, voxelOffset, VOXEL_VERTICES, GROUP_SIZE[g] * ring.count());
        }
        useParticles(glowProgram);
        var left = new Vector3f(-1, 0, 0).rotate(camera.rotation());
        var up = new Vector3f(0, 1, 0).rotate(camera.rotation());
        GL20.glUniform3f(glowProgram.uniform("CameraLeft"), left.x(), left.y(), left.z());
        GL20.glUniform3f(glowProgram.uniform("CameraUp"), up.x(), up.y(), up.z());
        int glowCount = glowProgram.uniform("ParticleCount");
        for (int g = 0; g < groups; g++) {
            var ring = GROUP_RING[g];
            if (ring.count() == 0) continue;
            GlStateManager._activeTexture(GL13.GL_TEXTURE0);
            GlStateManager._bindTexture(GROUP_TEXTURE[g]);
            GL20.glUniform1i(glowCount, ring.count());
            pointRing(glowProgram, GROUP_FIRST[g], ring.count());
            glowOffset += capture(GLOWS, glowOffset, QUAD_VERTICES, GROUP_SIZE[g] * ring.count());
        }
        GlStateManager._glUseProgram(ribbonProgram.id);
        GL20.glUniform1i(ribbonProgram.uniform("Segments"), RIBBON_SEGMENTS);
        pointRing(ribbonProgram, 0, RIBBON_SEGMENTS * 2);
        glowOffset += capture(GLOWS, glowOffset, QUAD_VERTICES, records * RIBBON_SEGMENTS * 2);
        GL11.glDisable(GL30.GL_RASTERIZER_DISCARD);
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, 0);
        GlStateManager._glUseProgram(0);
        GlStateManager._glBindVertexArray(0);
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        VOXELS.vertices = voxelOffset;
        GLOWS.vertices = glowOffset;
    }

    static void draw(List<EffectRenderer.RingCommand> commands, int count, Camera camera) {
        if (count == 0) return;
        if (!available()) {
            RingGeometry.draw(commands, count, camera, VOXELS, GLOWS);
            return;
        }
        if (count > MAX_GROUPS) {
            for (int first = 0; first < count; first += MAX_GROUPS) {
                int size = Math.min(MAX_GROUPS, count - first);
                draw(commands.subList(first, first + size), size, camera);
            }
            return;
        }
        buildTable(commands, count);
        simulate(camera);
        VOXELS.draw(EffectRenderPipelines.VOXEL, EffectRenderPipelines.WHITE_TEXTURE);
        GLOWS.draw(EffectRenderPipelines.GLOW, EffectRenderPipelines.GLOW_TEXTURE);
    }

    static void reload() {
        if (voxelProgram != null) GL20.glDeleteProgram(voxelProgram.id);
        if (glowProgram != null) GL20.glDeleteProgram(glowProgram.id);
        if (ribbonProgram != null) GL20.glDeleteProgram(ribbonProgram.id);
        if (simulateVao != -1) GL30.glDeleteVertexArrays(simulateVao);
        if (tableBuffer != -1) GlStateManager._glDeleteBuffers(tableBuffer);
        if (paletteTexture != -1) GlStateManager._deleteTexture(paletteTexture);
        DATA_TEXTURES.values().forEach(GlStateManager::_deleteTexture);
        DATA_TEXTURES.clear();
        Arrays.fill(GROUP_RING, null);
        Arrays.fill(PALETTES, null);
        voxelProgram = glowProgram = ribbonProgram = null;
        state = simulateVao = tableBuffer = paletteTexture = -1;
        VOXELS.close();
        GLOWS.close();
        RingGeometry.release();
    }
}
