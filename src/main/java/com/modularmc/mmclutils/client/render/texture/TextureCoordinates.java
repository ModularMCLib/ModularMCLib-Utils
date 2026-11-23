package com.modularmc.mmclutils.client.render.texture;

import com.modularmc.mmclutils.client.render.quad.MutableQuadView;
import com.modularmc.mmclutils.client.render.quad.QuadView;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo;
import net.minecraft.core.Direction;

import org.joml.Vector3fc;

/** 处理局部 UV、图集 UV 和按方块朝向锁定的 UV。局部范围为零至一。 */
public final class TextureCoordinates {

    private TextureCoordinates() {}

    public static float localU(TextureAtlasSprite sprite, float atlasU) {
        return (atlasU - sprite.getU0()) / (sprite.getU1() - sprite.getU0());
    }

    public static float localV(TextureAtlasSprite sprite, float atlasV) {
        return (atlasV - sprite.getV0()) / (sprite.getV1() - sprite.getV0());
    }

    public static MutableQuadView bake(MutableQuadView quad, TextureAtlasSprite sprite) {
        for (int vertex = 0; vertex < QuadView.VERTEX_COUNT; vertex++) {
            quad.uv(vertex, sprite.getU(quad.u(vertex)), sprite.getV(quad.v(vertex)));
        }
        return quad;
    }

    public static MutableQuadView unbake(MutableQuadView quad, TextureAtlasSprite sprite) {
        for (int vertex = 0; vertex < QuadView.VERTEX_COUNT; vertex++) {
            quad.uv(vertex, localU(sprite, quad.u(vertex)), localV(sprite, quad.v(vertex)));
        }
        return quad;
    }

    public static MutableQuadView replace(MutableQuadView quad, MaterialInfo material) {
        return bake(unbake(quad, quad.material().sprite()), material.sprite()).material(material);
    }

    /** 在局部 UV 中顺时针旋转四分之一圈的整数倍，再按需要翻转横轴和纵轴。 */
    public static MutableQuadView transform(MutableQuadView quad, int turns, boolean flipU, boolean flipV) {
        return transform(quad, turns, flipU, flipV, false);
    }

    public static MutableQuadView inverse(MutableQuadView quad, int turns, boolean flipU, boolean flipV) {
        return transform(quad, -turns, flipU, flipV, true);
    }

    private static MutableQuadView transform(MutableQuadView quad, int turns, boolean flipU, boolean flipV, boolean inverse) {
        for (int vertex = 0; vertex < QuadView.VERTEX_COUNT; vertex++) {
            float u = quad.u(vertex), v = quad.v(vertex);
            if (inverse && flipU) u = 1 - u;
            if (inverse && flipV) v = 1 - v;
            float x = u, y = v;
            switch (turns & 3) {
                case 1 -> {
                    x = 1 - v;
                    y = u;
                }
                case 2 -> {
                    x = 1 - u;
                    y = 1 - v;
                }
                case 3 -> {
                    x = v;
                    y = 1 - u;
                }
            }
            quad.uv(vertex, !inverse && flipU ? 1 - x : x, !inverse && flipV ? 1 - y : y);
        }
        return quad;
    }

    /** 根据相对于方块原点的位置生成指定方向的局部 UV。 */
    public static MutableQuadView lockToFace(MutableQuadView quad, Direction face) {
        for (int vertex = 0; vertex < QuadView.VERTEX_COUNT; vertex++) {
            Vector3fc pos = quad.position(vertex);
            switch (face) {
                case DOWN -> quad.uv(vertex, pos.x(), 1 - pos.z());
                case UP -> quad.uv(vertex, pos.x(), pos.z());
                case NORTH -> quad.uv(vertex, 1 - pos.x(), 1 - pos.y());
                case SOUTH -> quad.uv(vertex, pos.x(), 1 - pos.y());
                case WEST -> quad.uv(vertex, pos.z(), 1 - pos.y());
                case EAST -> quad.uv(vertex, 1 - pos.z(), 1 - pos.y());
            }
        }
        return quad;
    }
}
