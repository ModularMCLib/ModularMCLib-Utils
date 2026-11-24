package com.modularmc.mmclutils.client.model;

import com.modularmc.mmclutils.utils.ColorUtils;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.IQuadTransformer;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/** 可复用的四边形编辑器。调用方负责线程隔离，烘焙结果拥有独立顶点。 */
public final class MutableQuadView implements QuadView {

    private final Vector3f[] positions = { new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f() };
    private final long[] uv = new long[VERTEX_COUNT];
    private final int[] normals = new int[VERTEX_COUNT];
    private final int[] colors = new int[VERTEX_COUNT];
    private final int[] light = new int[VERTEX_COUNT];
    private Direction lightFace = Direction.UP;
    private @Nullable Direction cullFace;
    private MaterialInfo material;

    /** 创建一个编辑器，顶点初始在原点，调制色为白色。 */
    public MutableQuadView(MaterialInfo material) {
        clear(material);
    }

    /** 复制源四边形的顶点和属性，后续编辑与源视图相互独立。 */
    public MutableQuadView(QuadView source) {
        this(source.material());
        copyFrom(source);
    }

    /** 覆盖当前几何和材质，并返回当前编辑器。 */
    public MutableQuadView copyFrom(QuadView source) {
        for (int vertex = 0; vertex < VERTEX_COUNT; vertex++) {
            positions[vertex].set(source.position(vertex));
            uv[vertex] = source.packedUv(vertex);
            normals[vertex] = source.packedNormal(vertex);
            colors[vertex] = source.color(vertex);
            light[vertex] = source.packedLight(vertex);
        }
        lightFace = source.lightFace();
        cullFace = source.cullFace();
        material = source.material();
        return this;
    }

    /** 重置编辑器以构造另一个面。位置、UV 和法线清零，颜色恢复为白色。 */
    public MutableQuadView clear(MaterialInfo material) {
        for (Vector3f position : positions) position.zero();
        Arrays.fill(uv, 0);
        Arrays.fill(normals, 0);
        Arrays.fill(colors, -1);
        Arrays.fill(light, 0);
        lightFace = Direction.UP;
        cullFace = null;
        this.material = material;
        return this;
    }

    @Override
    public Vector3fc position(int vertex) {
        return positions[vertex];
    }

    /** 修改一个顶点的模型局部坐标，并返回当前编辑器。 */
    public MutableQuadView position(int vertex, float x, float y, float z) {
        positions[vertex].set(x, y, z);
        return this;
    }

    /** 复制向量到指定顶点。向量随后可由调用方复用。 */
    public MutableQuadView position(int vertex, Vector3fc position) {
        positions[vertex].set(position);
        return this;
    }

    @Override
    public long packedUv(int vertex) {
        return uv[vertex];
    }

    /** 写入以原版格式打包的 UV，返回当前编辑器。 */
    public MutableQuadView packedUv(int vertex, long packedUv) {
        uv[vertex] = packedUv;
        return this;
    }

    /** 写入 UV。烘焙时以当前值为图集坐标，使用局部 UV 时须事先映射到材质精灵。 */
    public MutableQuadView uv(int vertex, float u, float v) {
        return packedUv(vertex, (long) Float.floatToIntBits(u) << 32 | Integer.toUnsignedLong(Float.floatToIntBits(v)));
    }

    @Override
    public int packedNormal(int vertex) {
        return normals[vertex];
    }

    /** 写入 NeoForge 打包的顶点法线，零值沿用光照面。 */
    public MutableQuadView packedNormal(int vertex, int normal) {
        normals[vertex] = normal;
        return this;
    }

    /** 将单位向量打包为指定顶点的法线。 */
    public MutableQuadView normal(int vertex, Vector3fc normal) {
        return packedNormal(vertex, QuadUtils.packNormal(normal));
    }

    /** 用几何面法线填充未设置的顶点法线。退化面沿用光照方向。 */
    public MutableQuadView populateMissingNormals() {
        int flags = normalFlags();
        if (flags == (1 << VERTEX_COUNT) - 1) return this;
        var normal = QuadUtils.normal(this, new Vector3f());
        if (normal.lengthSquared() == 0) normal.set(lightFace.getStepX(), lightFace.getStepY(), lightFace.getStepZ());
        int packed = QuadUtils.packNormal(normal);
        for (int vertex = 0; vertex < VERTEX_COUNT; vertex++) {
            if ((flags & (1 << vertex)) == 0) normals[vertex] = packed;
        }
        return this;
    }

    @Override
    public int color(int vertex) {
        return colors[vertex];
    }

    @Override
    public int packedLight(int vertex) {
        return light[vertex];
    }

    /** 设置顶点的打包光照。 */
    public MutableQuadView packedLight(int vertex, int value) {
        light[vertex] = value;
        return this;
    }

    /** 设置单个顶点的 ARGB 调制色。 */
    public MutableQuadView color(int vertex, int argb) {
        colors[vertex] = argb;
        return this;
    }

    /** 为四个顶点应用相同的 ARGB 调制色。 */
    public MutableQuadView colorAll(int argb) {
        Arrays.fill(colors, argb);
        return this;
    }

    @Override
    public Direction lightFace() {
        return lightFace;
    }

    /** 设置用于光照的朝向，保留邻面剔除分组。 */
    public MutableQuadView lightFace(Direction face) {
        lightFace = face;
        return this;
    }

    @Override
    public @Nullable Direction cullFace() {
        return cullFace;
    }

    /** 设置邻面剔除分组，空值表示不按邻面剔除。 */
    public MutableQuadView cullFace(@Nullable Direction face) {
        cullFace = face;
        return this;
    }

    @Override
    public MaterialInfo material() {
        return material;
    }

    /** 替换材质并保留原 UV。需要重映射 UV 时使用 {@link QuadUtils#replace}。 */
    public MutableQuadView material(MaterialInfo value) {
        material = value;
        return this;
    }

    /** 产生可直接提交给原版的四边形。剔除分组仍由调用者维护。 */
    public BakedQuad bake() {
        int[] data = new int[VERTEX_COUNT * IQuadTransformer.STRIDE];
        for (int vertex = 0; vertex < VERTEX_COUNT; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE;
            Vector3f position = positions[vertex];
            data[offset + IQuadTransformer.POSITION] = Float.floatToRawIntBits(position.x);
            data[offset + IQuadTransformer.POSITION + 1] = Float.floatToRawIntBits(position.y);
            data[offset + IQuadTransformer.POSITION + 2] = Float.floatToRawIntBits(position.z);
            data[offset + IQuadTransformer.UV0] = (int) (uv[vertex] >>> 32);
            data[offset + IQuadTransformer.UV0 + 1] = (int) uv[vertex];
            data[offset + IQuadTransformer.COLOR] = ColorUtils.swapRedBlue(colors[vertex]);
            data[offset + IQuadTransformer.UV2] = light[vertex];
            data[offset + IQuadTransformer.NORMAL] = normals[vertex];
        }
        return new BakedQuad(data, material.tintIndex(), lightFace, material.sprite(), material.shade(), material.ambientOcclusion());
    }
}
