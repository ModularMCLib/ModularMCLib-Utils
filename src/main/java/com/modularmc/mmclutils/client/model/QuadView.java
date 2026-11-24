package com.modularmc.mmclutils.client.model;

import com.modularmc.mmclutils.utils.ColorUtils;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.IQuadTransformer;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/** 读取一个四边形的四个顶点和材质。视图可以复用，回调退出后应保留副本。 */
public interface QuadView {

    /** 一个四边形的顶点数。 */
    int VERTEX_COUNT = 4;

    /** 返回只读顶点坐标，索引范围为零至三。 */
    Vector3fc position(int vertex);

    /** 返回打包的图集坐标，高位为 U，低位为 V。 */
    long packedUv(int vertex);

    /** 返回 NeoForge 编码的法线。零值表示使用面朝向。 */
    int packedNormal(int vertex);

    /** 返回顶点的 ARGB 调制色。 */
    int color(int vertex);

    /** 返回原版打包的顶点光照。 */
    default int packedLight(int vertex) {
        return 0;
    }

    /** 返回用于光照的面朝向。 */
    Direction lightFace();

    /** 返回用于邻面遮挡判断的朝向。空值表示始终绘制。 */
    @Nullable
    Direction cullFace();

    /** 返回原版材质设置。 */
    MaterialInfo material();

    /** 返回指定顶点的图集横坐标，范围取决于材质精灵。 */
    default float u(int vertex) {
        return Float.intBitsToFloat((int) (packedUv(vertex) >>> 32));
    }

    /** 返回指定顶点的图集纵坐标，范围取决于材质精灵。 */
    default float v(int vertex) {
        return Float.intBitsToFloat((int) packedUv(vertex));
    }

    /** 返回显式顶点法线的位掩码，第零位至第三位按顶点顺序排列。 */
    default int normalFlags() {
        int flags = 0;
        for (int vertex = 0; vertex < VERTEX_COUNT; vertex++) {
            if (packedNormal(vertex) != 0) flags |= 1 << vertex;
        }
        return flags;
    }

    /** 判断是否存在至少一个显式顶点法线。 */
    default boolean hasVertexNormals() {
        return (packedNormal(0) | packedNormal(1) | packedNormal(2) | packedNormal(3)) != 0;
    }

    /** 包装原版烘焙四边形。传入该面所在的剔除分组，通用面传空值。 */
    static QuadView of(BakedQuad quad, @Nullable Direction cullFace) {
        return new Baked(quad, cullFace);
    }

    /** 1.21.1 中随四边形保存的精灵、染色和光照选项。渲染层由所属模型提供。 */
    record MaterialInfo(TextureAtlasSprite sprite, int tintIndex, boolean shade, boolean ambientOcclusion) {}

    /** 提供原版烘焙面的只读视图，保留调用者指定的剔除分组。 */
    record Baked(BakedQuad quad, @Nullable Direction cullFace) implements QuadView {

        @Override
        public Vector3fc position(int vertex) {
            int offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
            int[] data = quad.getVertices();
            return new Vector3f(Float.intBitsToFloat(data[offset]), Float.intBitsToFloat(data[offset + 1]),
                    Float.intBitsToFloat(data[offset + 2]));
        }

        @Override
        public long packedUv(int vertex) {
            int offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
            int[] data = quad.getVertices();
            return (long) data[offset] << 32 | Integer.toUnsignedLong(data[offset + 1]);
        }

        @Override
        public int packedNormal(int vertex) {
            return quad.getVertices()[vertex * IQuadTransformer.STRIDE + IQuadTransformer.NORMAL] & 0xFFFFFF;
        }

        @Override
        public int color(int vertex) {
            return ColorUtils.swapRedBlue(quad.getVertices()[vertex * IQuadTransformer.STRIDE + IQuadTransformer.COLOR]);
        }

        @Override
        public int packedLight(int vertex) {
            return quad.getVertices()[vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV2];
        }

        @Override
        public Direction lightFace() {
            return quad.getDirection();
        }

        @Override
        public MaterialInfo material() {
            return new MaterialInfo(quad.getSprite(), quad.getTintIndex(), quad.isShade(), quad.hasAmbientOcclusion());
        }
    }
}
