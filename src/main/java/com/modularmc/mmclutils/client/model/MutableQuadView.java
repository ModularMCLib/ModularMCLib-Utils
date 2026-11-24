package com.modularmc.mmclutils.client.model;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.quad.BakedColors;
import net.neoforged.neoforge.client.model.quad.BakedNormals;

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
    private Direction lightFace = Direction.UP;
    private @Nullable Direction cullFace;
    private MaterialInfo material;

    public MutableQuadView(MaterialInfo material) {
        this.material = material;
        Arrays.fill(colors, -1);
    }

    public MutableQuadView(QuadView source) {
        this(source.material());
        copyFrom(source);
    }

    public MutableQuadView copyFrom(QuadView source) {
        for (int vertex = 0; vertex < VERTEX_COUNT; vertex++) {
            positions[vertex].set(source.position(vertex));
            uv[vertex] = source.packedUv(vertex);
            normals[vertex] = source.packedNormal(vertex);
            colors[vertex] = source.color(vertex);
        }
        lightFace = source.lightFace();
        cullFace = source.cullFace();
        material = source.material();
        return this;
    }

    @Override
    public Vector3fc position(int vertex) {
        return positions[vertex];
    }

    public MutableQuadView position(int vertex, float x, float y, float z) {
        positions[vertex].set(x, y, z);
        return this;
    }

    public MutableQuadView position(int vertex, Vector3fc position) {
        positions[vertex].set(position);
        return this;
    }

    @Override
    public long packedUv(int vertex) {
        return uv[vertex];
    }

    public MutableQuadView packedUv(int vertex, long packedUv) {
        uv[vertex] = packedUv;
        return this;
    }

    public MutableQuadView uv(int vertex, float u, float v) {
        return packedUv(vertex, UVPair.pack(u, v));
    }

    @Override
    public int packedNormal(int vertex) {
        return normals[vertex];
    }

    public MutableQuadView packedNormal(int vertex, int normal) {
        normals[vertex] = normal;
        return this;
    }

    public MutableQuadView normal(int vertex, Vector3fc normal) {
        return packedNormal(vertex, BakedNormals.pack(normal));
    }

    @Override
    public int color(int vertex) {
        return colors[vertex];
    }

    public MutableQuadView color(int vertex, int argb) {
        colors[vertex] = argb;
        return this;
    }

    public MutableQuadView colorAll(int argb) {
        Arrays.fill(colors, argb);
        return this;
    }

    @Override
    public Direction lightFace() {
        return lightFace;
    }

    public MutableQuadView lightFace(Direction face) {
        lightFace = face;
        return this;
    }

    @Override
    public @Nullable Direction cullFace() {
        return cullFace;
    }

    public MutableQuadView cullFace(@Nullable Direction face) {
        cullFace = face;
        return this;
    }

    @Override
    public MaterialInfo material() {
        return material;
    }

    public MutableQuadView material(MaterialInfo value) {
        material = value;
        return this;
    }

    /** 产生可直接提交给原版的四边形。剔除分组仍由调用者维护。 */
    public BakedQuad bake() {
        return new BakedQuad(new Vector3f(positions[0]), new Vector3f(positions[1]), new Vector3f(positions[2]), new Vector3f(positions[3]),
                uv[0], uv[1], uv[2], uv[3], lightFace, material,
                BakedNormals.of(normals[0], normals[1], normals[2], normals[3]), BakedColors.of(colors[0], colors[1], colors[2], colors[3]));
    }
}
