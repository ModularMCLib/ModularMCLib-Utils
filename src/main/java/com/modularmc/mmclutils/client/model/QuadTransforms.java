package com.modularmc.mmclutils.client.model;

import net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.quad.BakedNormals;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Predicate;

/** 可组合的四边形变换。返回假表示终止处理并丢弃该面。 */
public final class QuadTransforms {

    private QuadTransforms() {}

    /** 生成模型局部平移，位移沿 X、Y、Z 分量表示。 */
    public static Transform offset(float x, float y, float z) {
        return quad -> {
            for (int vertex = 0; vertex < QuadView.VERTEX_COUNT; vertex++) {
                Vector3fc point = quad.position(vertex);
                quad.position(vertex, point.x() + x, point.y() + y, point.z() + z);
            }
            return true;
        };
    }

    /** 旋转、缩放或平移顶点，并重新计算光照面。几何变换后由调用方决定剔除分组。 */
    public static Transform transform(Matrix4fc matrix) {
        var pose = new Matrix4f(matrix);
        var normalMatrix = pose.normal(new Matrix3f());
        return quad -> {
            var position = new Vector3f();
            var normal = new Vector3f();
            for (int vertex = 0; vertex < QuadView.VERTEX_COUNT; vertex++) {
                position.set(quad.position(vertex)).mulPosition(pose);
                quad.position(vertex, position);
                int packed = quad.packedNormal(vertex);
                if (packed != 0) {
                    BakedNormals.unpack(packed, normal);
                    normal.mul(normalMatrix).normalize();
                    quad.normal(vertex, normal);
                }
            }
            quad.lightFace(QuadUtils.lightFace(QuadUtils.normal(quad, normal)));
            quad.cullFace(null);
            return true;
        };
    }

    /** 创建绕模型局部中心的旋转。旋转后重算光照面并清除剔除分组。 */
    public static Transform rotate(Quaternionfc rotation, Vector3fc center) {
        return transform(new Matrix4f().translate(center).rotate(rotation).translate(-center.x(), -center.y(), -center.z()));
    }

    /** 创建相对于指定中心的轴向缩放。为保证法线可变换，各轴缩放系数须非零。 */
    public static Transform scale(float x, float y, float z, Vector3fc center) {
        return transform(new Matrix4f().translate(center).scale(x, y, z).translate(-center.x(), -center.y(), -center.z()));
    }

    /** 将面上各顶点限制在给定局部包围盒内，保留原顶点属性。 */
    public static Transform clamp(AABB bounds) {
        return quad -> {
            for (int vertex = 0; vertex < QuadView.VERTEX_COUNT; vertex++) {
                Vector3fc point = quad.position(vertex);
                quad.position(vertex, (float) Math.clamp(point.x(), bounds.minX, bounds.maxX),
                        (float) Math.clamp(point.y(), bounds.minY, bounds.maxY),
                        (float) Math.clamp(point.z(), bounds.minZ, bounds.maxZ));
            }
            return true;
        };
    }

    /** 为所有顶点写入同一个 ARGB。指定 {@code clearTint} 时同时清除材质染色索引。 */
    public static Transform color(int argb, boolean clearTint) {
        return quad -> {
            quad.colorAll(argb);
            if (clearTint) {
                MaterialInfo material = quad.material();
                quad.material(new MaterialInfo(material.sprite(), material.layer(), material.itemRenderType(), -1,
                        material.shade(), material.lightEmission(), material.ambientOcclusion()));
            }
            return true;
        };
    }

    /** 重映射图集 UV。新材质同时提供与图集匹配的渲染层。 */
    public static Transform retexture(MaterialInfo replacement) {
        return quad -> {
            QuadUtils.replace(quad, replacement);
            return true;
        };
    }

    /** 用指定方向的方块局部坐标投影 UV，再映射到当前材质精灵。 */
    public static Transform lockUv(Direction face) {
        return quad -> {
            QuadUtils.bake(QuadUtils.lockToFace(quad, face), quad.material().sprite());
            return true;
        };
    }

    /** 在裁切前记录轴对齐面的属性，随后按裁切后的顶点重算 UV 和颜色。 */
    public static Transform clampInterpolated(AABB bounds) {
        Transform clamp = clamp(bounds);
        return quad -> {
            Direction.Axis axis = quad.lightFace().getAxis();
            if (!QuadUtils.isParallel(quad, axis, 0.00001f)) return clamp.apply(quad);
            var source = new MutableQuadView(quad);
            clamp.apply(quad);
            QuadUtils.interpolate(source, quad, axis);
            return true;
        };
    }

    /** 用谓词决定是否保留该面。谓词只在变换期间读取当前视图。 */
    public static Transform filter(Predicate<? super QuadView> predicate) {
        return predicate::test;
    }

    /** 在调用线程中原地修改可写视图。调用方不得在之后复用被拒绝的面。 */
    @FunctionalInterface
    public interface Transform {

        /** 返回真允许该面继续执行后续变换。 */
        boolean apply(MutableQuadView quad);

        /** 将两个变换按顺序组合。前一变换丢弃面时跳过后一变换。 */
        default Transform andThen(Transform next) {
            return quad -> apply(quad) && next.apply(quad);
        }
    }
}
