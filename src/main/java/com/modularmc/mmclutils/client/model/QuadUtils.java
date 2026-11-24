package com.modularmc.mmclutils.client.model;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.quad.BakedNormals;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/** 构造和变换原版四边形，处理顶点法线、光照方向、颜色插值和图集 UV。 */
public final class QuadUtils {

    private QuadUtils() {}

    /** 逐面应用变换，收集通过变换的烘焙面。变换收到可复用编辑器，应在返回前完成修改。 */
    public static ObjectList<Face> transform(Iterable<Face> source, QuadTransforms.Transform transform) {
        var output = new ObjectArrayList<Face>();
        MutableQuadView editor = null;
        for (Face face : source) {
            QuadView view = QuadView.of(face.quad(), face.cullFace());
            if (editor == null) editor = new MutableQuadView(view);
            else editor.copyFrom(view);
            if (transform.apply(editor)) output.add(new Face(editor.bake(), editor.cullFace()));
        }
        return new ObjectImmutableList<>(output);
    }

    /** 取出同一剔除分组的烘焙面。空值选择无需邻面剔除的分组。 */
    public static ObjectList<BakedQuad> forFace(Iterable<Face> source, @Nullable Direction cullFace) {
        var output = new ObjectArrayList<BakedQuad>();
        for (Face face : source) {
            if (face.cullFace() == cullFace) output.add(face.quad());
        }
        return new ObjectImmutableList<>(output);
    }

    /** 以方块单位构造盒子一面。盒子内侧的面不参与邻方块剔除。 */
    public static Face boxFace(AABB box, Direction face, MaterialInfo material) {
        var quad = new MutableQuadView(material).lightFace(face);
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
        boolean onEdge = switch (face) {
            case DOWN -> {
                quad.position(0, x0, y0, z0).position(1, x1, y0, z0).position(2, x1, y0, z1).position(3, x0, y0, z1);
                yield y0 == 0;
            }
            case UP -> {
                quad.position(0, x0, y1, z1).position(1, x1, y1, z1).position(2, x1, y1, z0).position(3, x0, y1, z0);
                yield y1 == 1;
            }
            case NORTH -> {
                quad.position(0, x1, y0, z0).position(1, x0, y0, z0).position(2, x0, y1, z0).position(3, x1, y1, z0);
                yield z0 == 0;
            }
            case SOUTH -> {
                quad.position(0, x0, y0, z1).position(1, x1, y0, z1).position(2, x1, y1, z1).position(3, x0, y1, z1);
                yield z1 == 1;
            }
            case WEST -> {
                quad.position(0, x0, y0, z0).position(1, x0, y0, z1).position(2, x0, y1, z1).position(3, x0, y1, z0);
                yield x0 == 0;
            }
            case EAST -> {
                quad.position(0, x1, y0, z1).position(1, x1, y0, z0).position(2, x1, y1, z0).position(3, x1, y1, z1);
                yield x1 == 1;
            }
        };
        bake(lockToFace(quad, face), material.sprite());
        return new Face(quad.bake(), onEdge ? face : null);
    }

    /** 用指定材质生成包围盒的六个烘焙面。 */
    public static ObjectList<Face> box(AABB bounds, MaterialInfo material) {
        var faces = new ObjectArrayList<Face>(6);
        for (Direction direction : Direction.values()) faces.add(boxFace(bounds, direction, material));
        return new ObjectImmutableList<>(faces);
    }

    /** 烘焙四边形及其剔除分组。空方向表示通用面。 */
    public record Face(BakedQuad quad, @Nullable Direction cullFace) {}

    /** 将顶点法线写入调用方的向量。缺少独立法线时使用光照朝向。 */
    public static Vector3f vertexNormal(QuadView quad, int vertex, Vector3f output) {
        int packed = quad.packedNormal(vertex);
        if (packed != 0) {
            BakedNormals.unpack(packed, output);
            return output;
        }
        Direction face = quad.lightFace();
        return output.set(face.getStepX(), face.getStepY(), face.getStepZ());
    }

    /** 按顶点顺序计算面法线。退化的面返回零向量。 */
    public static Vector3f normal(QuadView quad, Vector3f result) {
        Vector3fc a = quad.position(0), b = quad.position(1), c = quad.position(2), d = quad.position(3);
        float x = d.x() - b.x(), y = d.y() - b.y(), z = d.z() - b.z();
        result.set(c).sub(a).cross(x, y, z);
        return result.lengthSquared() == 0 ? result : result.normalize();
    }

    /** 按单位法线的主要分量选择用于光照的原版方向。 */
    public static Direction lightFace(Vector3fc normal) {
        float x = Math.abs(normal.x()), y = Math.abs(normal.y()), z = Math.abs(normal.z());
        if (x > y && x >= z) return normal.x() > 0 ? Direction.EAST : Direction.WEST;
        if (z > y) return normal.z() > 0 ? Direction.SOUTH : Direction.NORTH;
        return normal.y() < 0 ? Direction.DOWN : Direction.UP;
    }

    /** 判断四个顶点是否落在垂直于指定轴的同一平面内。 */
    public static boolean isParallel(QuadView quad, Direction.Axis axis, float tolerance) {
        Vector3fc first = quad.position(0);
        double coordinate = axis.choose(first.x(), first.y(), first.z());
        for (int i = 1; i < QuadView.VERTEX_COUNT; i++) {
            Vector3fc point = quad.position(i);
            if (Math.abs(axis.choose(point.x(), point.y(), point.z()) - coordinate) > tolerance) return false;
        }
        return true;
    }

    /** 用源四边形的两个三角形重算目标顶点的颜色与 UV。两个视图须来自同一平面。 */
    public static void interpolate(QuadView source, MutableQuadView target, Direction.Axis droppedAxis) {
        int x = droppedAxis == Direction.Axis.X ? 1 : 0;
        int y = droppedAxis == Direction.Axis.Z ? 1 : 2;
        for (int i = 0; i < QuadView.VERTEX_COUNT; i++) {
            Vector3fc point = target.position(i);
            if (!sample(source, target, i, point.get(x), point.get(y), x, y, 1, 2, false)) {
                sample(source, target, i, point.get(x), point.get(y), x, y, 2, 3, true);
            }
        }
    }

    private static boolean sample(QuadView source, MutableQuadView target, int index, float px, float py,
                                  int axisX, int axisY, int b, int c, boolean extend) {
        Vector3fc origin = source.position(0), v1 = source.position(b), v2 = source.position(c);
        float x1 = v1.get(axisX) - origin.get(axisX), y1 = v1.get(axisY) - origin.get(axisY);
        float x2 = v2.get(axisX) - origin.get(axisX), y2 = v2.get(axisY) - origin.get(axisY);
        float x = px - origin.get(axisX), y = py - origin.get(axisY);
        float area = x1 * y2 - y1 * x2;
        if (area == 0) return false;
        float w1 = (x * y2 - y * x2) / area, w2 = (x1 * y - y1 * x) / area;
        float w0 = 1 - w1 - w2;
        if (!extend && Math.min(w0, Math.min(w1, w2)) < -0.00001f) return false;
        target.uv(index, w0 * source.u(0) + w1 * source.u(b) + w2 * source.u(c),
                w0 * source.v(0) + w1 * source.v(b) + w2 * source.v(c));
        int aColor = source.color(0), bColor = source.color(b), cColor = source.color(c);
        target.color(index, ARGB.color(channel(aColor, bColor, cColor, 24, w0, w1, w2),
                channel(aColor, bColor, cColor, 16, w0, w1, w2), channel(aColor, bColor, cColor, 8, w0, w1, w2),
                channel(aColor, bColor, cColor, 0, w0, w1, w2)));
        return true;
    }

    private static int channel(int a, int b, int c, int shift, float w0, float w1, float w2) {
        return Math.clamp(Math.round(w0 * (a >>> shift & 255) + w1 * (b >>> shift & 255) + w2 * (c >>> shift & 255)), 0, 255);
    }

    /** 将图集横坐标转换到贴图以零至一表示的局部坐标。 */
    public static float localU(TextureAtlasSprite sprite, float atlasU) {
        return (atlasU - sprite.getU0()) / (sprite.getU1() - sprite.getU0());
    }

    /** 将图集纵坐标转换到贴图以零至一表示的局部坐标。 */
    public static float localV(TextureAtlasSprite sprite, float atlasV) {
        return (atlasV - sprite.getV0()) / (sprite.getV1() - sprite.getV0());
    }

    /** 将面上的精灵局部 UV 原地换算为图集 UV。 */
    public static MutableQuadView bake(MutableQuadView quad, TextureAtlasSprite sprite) {
        for (int vertex = 0; vertex < QuadView.VERTEX_COUNT; vertex++) {
            quad.uv(vertex, sprite.getU(quad.u(vertex)), sprite.getV(quad.v(vertex)));
        }
        return quad;
    }

    /** 将面上的图集 UV 原地换算为指定精灵的局部 UV。 */
    public static MutableQuadView unbake(MutableQuadView quad, TextureAtlasSprite sprite) {
        for (int vertex = 0; vertex < QuadView.VERTEX_COUNT; vertex++) {
            quad.uv(vertex, localU(sprite, quad.u(vertex)), localV(sprite, quad.v(vertex)));
        }
        return quad;
    }

    /** 替换材质并将旧图集 UV 重映射到新精灵，保留 UV 在精灵内的位置。 */
    public static MutableQuadView replace(MutableQuadView quad, MaterialInfo material) {
        return bake(unbake(quad, quad.material().sprite()), material.sprite()).material(material);
    }

    /** 在局部 UV 中顺时针旋转四分之一圈的整数倍，再按需要翻转横轴和纵轴。 */
    public static MutableQuadView transform(MutableQuadView quad, int turns, boolean flipU, boolean flipV) {
        return transform(quad, turns, flipU, flipV, false);
    }

    /** 撤销使用同样参数的 UV 变换，翻转与旋转按逆序应用。 */
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
