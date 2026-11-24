package com.modularmc.mmclutils.client.model;

import com.modularmc.mmclutils.client.model.QuadUtils.Face;
import com.modularmc.mmclutils.client.model.QuadView.MaterialInfo;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** 切分四边形并为子面指定贴图。连接条件和材质由调用方决定。 */
public final class ConnectedTextureUtils {

    private static final Direction[] DIRECTIONS = Direction.values();

    private ConnectedTextureUtils() {}

    /**
     * 包装能根据相邻方块更换表面的模型。无世界上下文的渲染沿用父模型。
     *
     * @param parent 提供基础几何和粒子材质的模型
     * @param rule   根据世界上下文确定每个面的输出
     * @return 保留父模型随机变体和光照行为的方块模型
     */
    public static BakedModel wrap(BakedModel parent, FaceRule rule) {
        return new ConnectedModel(parent, rule);
    }

    /** 四份材质按从左到右、从上到下排列。每个子面映射整个新贴图。 */
    public static ObjectList<Face> quadrants(Face input, MaterialInfo topLeft, MaterialInfo topRight,
                                             MaterialInfo bottomLeft, MaterialInfo bottomRight) {
        return ObjectList.of(
                patch(input, 0, 0, 0.5f, 0.5f, topLeft),
                patch(input, 0.5f, 0, 1, 0.5f, topRight),
                patch(input, 0, 0.5f, 0.5f, 1, bottomLeft),
                patch(input, 0.5f, 0.5f, 1, 1, bottomRight));
    }

    /** 参数域从顶点零出发，横轴沿零至三，纵轴沿零至一。子面使用整个目标精灵。 */
    public static Face patch(Face input, float u0, float v0, float u1, float v1, MaterialInfo material) {
        return patch(input, u0, v0, u1, v1, new Tile(material, 0, 0, 1, 1));
    }

    /** 在顶点参数域内切分四边形，并将目标精灵的指定子区域铺到整个子面。 */
    public static Face patch(Face input, float u0, float v0, float u1, float v1, Tile tile) {
        checkRegion(u0, v0, u1, v1);
        QuadView source = QuadView.of(input.quad(), input.cullFace());
        var output = new MutableQuadView(source);
        var a = new Vector3f();
        var b = new Vector3f();
        for (int vertex = 0; vertex < QuadView.VERTEX_COUNT; vertex++) {
            float u = vertex < 2 ? u0 : u1;
            float v = vertex == 0 || vertex == 3 ? v0 : v1;
            a.set(source.position(0)).lerp(source.position(3), u);
            b.set(source.position(1)).lerp(source.position(2), u);
            output.position(vertex, a.lerp(b, v));
        }
        QuadUtils.interpolate(source, output, source.lightFace().getAxis());
        output.uv(0, tile.minU(), tile.minV()).uv(1, tile.minU(), tile.maxV())
                .uv(2, tile.maxU(), tile.maxV()).uv(3, tile.maxU(), tile.minV());
        return bake(output, tile);
    }

    /** 按光照面的方块局部坐标切分轴对齐矩形，没有覆盖的象限会被略过。 */
    public static ObjectList<Face> quadrants(Face input, Tile topLeft, Tile topRight, Tile bottomLeft, Tile bottomRight) {
        var result = new ObjectArrayList<Face>(4);
        Tile[] tiles = { topLeft, topRight, bottomLeft, bottomRight };
        for (int i = 0; i < tiles.length; i++) {
            float u0 = (i & 1) * 0.5f;
            float v0 = (i >>> 1) * 0.5f;
            Face face = tile(input, u0, v0, u0 + 0.5f, v0 + 0.5f, tiles[i]);
            if (face != null) result.add(face);
        }
        return new ObjectImmutableList<>(result);
    }

    /**
     * 按 {@link QuadUtils#lockToFace} 的方块坐标裁切轴对齐矩形，将裁切区间线性对应到贴图子区域。
     * 顶点顺序、调制色、法线和剔除分组随几何裁切保留。仅接触边界时返回空值。
     *
     * @param input 原始四边形，须为垂直于光照面轴向的矩形
     * @param u0    裁切区间左边界
     * @param v0    裁切区间上边界
     * @param u1    裁切区间右边界
     * @param v1    裁切区间下边界
     * @param tile  子面材质和目标精灵内的 UV 范围
     * @return 裁切后的烘焙面，或交集无面积时的空值
     */
    public static @Nullable Face tile(Face input, float u0, float v0, float u1, float v1, Tile tile) {
        checkRegion(u0, v0, u1, v1);
        QuadView source = QuadView.of(input.quad(), input.cullFace());
        Direction direction = source.lightFace();
        var output = QuadUtils.lockToFace(new MutableQuadView(source), direction);
        float minU = u1, minV = v1, maxU = u0, maxV = v0;
        for (int vertex = 0; vertex < QuadView.VERTEX_COUNT; vertex++) {
            float u = output.u(vertex), v = output.v(vertex);
            float clampedU = Math.clamp(u, u0, u1);
            float clampedV = Math.clamp(v, v0, v1);
            minU = Math.min(minU, clampedU);
            minV = Math.min(minV, clampedV);
            maxU = Math.max(maxU, clampedU);
            maxV = Math.max(maxV, clampedV);
            moveVertex(output, vertex, direction, clampedU - u, clampedV - v);
        }
        if (minU >= maxU || minV >= maxV) return null;

        QuadUtils.interpolate(source, output, direction.getAxis());
        QuadUtils.lockToFace(output, direction);
        for (int vertex = 0; vertex < QuadView.VERTEX_COUNT; vertex++) {
            float u = (output.u(vertex) - u0) / (u1 - u0);
            float v = (output.v(vertex) - v0) / (v1 - v0);
            output.uv(vertex, Mth.lerp(u, tile.minU(), tile.maxU()), Mth.lerp(v, tile.minV(), tile.maxV()));
        }
        return bake(output, tile);
    }

    /** 两条相邻边及夹角按三个位存储，可作为材质数组索引。 */
    public static int connectionMask(boolean horizontal, boolean vertical, boolean diagonal) {
        return (horizontal ? 1 : 0) | (vertical ? 2 : 0) | (horizontal && vertical && diagonal ? 4 : 0);
    }

    /** 连接纹理的表面规则。模型构建线程可以并发调用，从传入的方块视图读取邻接状态。 */
    @FunctionalInterface
    public interface FaceRule {

        /**
         * 为当前面选择替换面。返回空值保留原面，返回空集合移除该面。
         *
         * @param level 可读取邻接状态和模型数据的方块视图
         * @param pos   当前位置，仅在本次调用期间使用
         * @param state 当前正在构建模型的方块状态
         * @param face  父模型的烘焙面及剔除分组
         * @return 按绘制顺序排列的替换面，或表示保留原面的空值
         */
        @Nullable
        Iterable<Face> replace(BlockAndTintGetter level, BlockPos pos, BlockState state, Face face);
    }

    /**
     * 目标材质在精灵局部坐标中使用的矩形，坐标在零至一之间。
     *
     * @param material 目标精灵和渲染材质设置
     * @param minU     左边界
     * @param minV     上边界
     * @param maxU     右边界
     * @param maxV     下边界
     */
    public record Tile(MaterialInfo material, float minU, float minV, float maxU, float maxV) {

        /** @throws IllegalArgumentException 坐标越界或没有面积 */
        public Tile {
            checkRegion(minU, minV, maxU, maxV);
        }
    }

    private static void checkRegion(float u0, float v0, float u1, float v1) {
        if (!(u0 >= 0 && u1 <= 1 && u0 < u1 && v0 >= 0 && v1 <= 1 && v0 < v1)) {
            throw new IllegalArgumentException("子区域须落在零至一之间并具有面积");
        }
    }

    private static Face bake(MutableQuadView quad, Tile tile) {
        QuadUtils.bake(quad.material(tile.material()), tile.material().sprite());
        return new Face(quad.bake(), quad.cullFace());
    }

    private static void moveVertex(MutableQuadView quad, int vertex, Direction face, float u, float v) {
        Vector3fc pos = quad.position(vertex);
        switch (face) {
            case DOWN -> quad.position(vertex, pos.x() + u, pos.y(), pos.z() - v);
            case UP -> quad.position(vertex, pos.x() + u, pos.y(), pos.z() + v);
            case NORTH -> quad.position(vertex, pos.x() - u, pos.y() - v, pos.z());
            case SOUTH -> quad.position(vertex, pos.x() + u, pos.y() - v, pos.z());
            case WEST -> quad.position(vertex, pos.x(), pos.y() - v, pos.z() + u);
            case EAST -> quad.position(vertex, pos.x(), pos.y() - v, pos.z() - u);
        }
    }

    private static final class ConnectedModel extends BakedModelWrapper<BakedModel> {

        private static final ModelProperty<Context> CONTEXT = new ModelProperty<>();
        private final FaceRule rule;

        private ConnectedModel(BakedModel parent, FaceRule rule) {
            super(parent);
            this.rule = rule;
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            return super.getModelData(level, pos, state, data).derive().with(CONTEXT, new Context(level, pos.immutable())).build();
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random,
                                        ModelData data, @Nullable RenderType layer) {
            Context context = data.get(CONTEXT);
            if (context == null || state == null) return super.getQuads(state, direction, random, data, layer);
            var output = new ObjectArrayList<BakedQuad>();
            // 同一个方块的各剔除组使用同一个加权变体，替换规则可以改变面的剔除方向。
            long seed = state.getSeed(context.pos());
            RandomSource sample = RandomSource.create(seed);
            append(context, state, null, direction, sample, data, layer, output);
            for (Direction side : DIRECTIONS) {
                sample.setSeed(seed);
                append(context, state, side, direction, sample, data, layer, output);
            }
            return output;
        }

        private void append(Context context, BlockState state, @Nullable Direction source, @Nullable Direction requested,
                            RandomSource random, ModelData data, @Nullable RenderType layer, List<BakedQuad> output) {
            for (BakedQuad quad : originalModel.getQuads(state, source, random, data, layer)) {
                Iterable<Face> replacement = rule.replace(context.level(), context.pos(), state, new Face(quad, source));
                if (replacement == null) {
                    if (source == requested) output.add(quad);
                } else {
                    for (Face face : replacement) if (face.cullFace() == requested) output.add(face.quad());
                }
            }
        }
    }

    private record Context(BlockAndTintGetter level, BlockPos pos) {}
}
