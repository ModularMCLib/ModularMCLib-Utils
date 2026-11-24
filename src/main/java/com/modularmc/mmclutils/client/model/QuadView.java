package com.modularmc.mmclutils.client.model;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo;
import net.minecraft.core.Direction;

import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/** 读取一个四边形的四个顶点和材质。视图可以复用，回调退出后应保留副本。 */
public interface QuadView {

    /** 一个四边形的顶点数。 */
    int VERTEX_COUNT = BakedQuad.VERTEX_COUNT;

    /** 返回只读顶点坐标，索引范围为零至三。 */
    Vector3fc position(int vertex);

    /** 返回使用原版 UVPair 编码的图集坐标。 */
    long packedUv(int vertex);

    /** 返回 NeoForge 编码的法线。零值表示使用面朝向。 */
    int packedNormal(int vertex);

    /** 返回顶点的 ARGB 调制色。 */
    int color(int vertex);

    /** 返回用于光照的面朝向。 */
    Direction lightFace();

    /** 返回用于邻面遮挡判断的朝向。空值表示始终绘制。 */
    @Nullable
    Direction cullFace();

    /** 返回原版材质设置。 */
    MaterialInfo material();

    /** 返回指定顶点的图集横坐标，范围取决于材质精灵。 */
    default float u(int vertex) {
        return UVPair.unpackU(packedUv(vertex));
    }

    /** 返回指定顶点的图集纵坐标，范围取决于材质精灵。 */
    default float v(int vertex) {
        return UVPair.unpackV(packedUv(vertex));
    }

    /** 包装原版烘焙四边形。传入该面所在的剔除分组，通用面传空值。 */
    static QuadView of(BakedQuad quad, @Nullable Direction cullFace) {
        return new Baked(quad, cullFace);
    }

    /** 提供原版烘焙面的只读视图，保留调用者指定的剔除分组。 */
    record Baked(BakedQuad quad, @Nullable Direction cullFace) implements QuadView {

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
}
