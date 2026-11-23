package com.modularmc.mmclutils.client.render.quad;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo;
import net.minecraft.core.Direction;

import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/** 读取一个四边形的四个顶点和材质。视图可以复用，回调退出后应保留副本。 */
public interface QuadView {

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

    default float u(int vertex) {
        return UVPair.unpackU(packedUv(vertex));
    }

    default float v(int vertex) {
        return UVPair.unpackV(packedUv(vertex));
    }

    /** 包装原版烘焙四边形。传入该面所在的剔除分组，通用面传空值。 */
    static QuadView of(BakedQuad quad, @Nullable Direction cullFace) {
        return new BakedQuadView(quad, cullFace);
    }
}
