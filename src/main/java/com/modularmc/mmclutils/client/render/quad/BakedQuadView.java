package com.modularmc.mmclutils.client.render.quad;

import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo;
import net.minecraft.core.Direction;

import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/** 提供原版烘焙面的只读视图，保留调用者指定的剔除分组。 */
public record BakedQuadView(BakedQuad quad, @Nullable Direction cullFace) implements QuadView {

    @Override
    public Vector3fc position(int vertex) {
        return quad.position(vertex);
    }

    @Override
    public long packedUv(int vertex) {
        return quad.packedUV(vertex);
    }

    @Override
    public int packedNormal(int vertex) {
        return quad.bakedNormals().normal(vertex);
    }

    @Override
    public int color(int vertex) {
        return quad.bakedColors().color(vertex);
    }

    @Override
    public Direction lightFace() {
        return quad.direction();
    }

    @Override
    public MaterialInfo material() {
        return quad.materialInfo();
    }
}
