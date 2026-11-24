package com.modularmc.mmclutils.client.model;

import com.modularmc.mmclutils.client.render.quad.MutableQuadView;
import com.modularmc.mmclutils.client.render.quad.QuadGeometry;
import com.modularmc.mmclutils.client.render.quad.QuadMeshes;
import com.modularmc.mmclutils.client.render.quad.QuadView;

import net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo;

import it.unimi.dsi.fastutil.objects.ObjectList;
import org.joml.Vector3f;

/** 切分四边形并为子面指定贴图。连接条件和材质由调用方决定。 */
public final class ConnectedModels {

    private ConnectedModels() {}

    /** 四份材质按从左到右、从上到下排列。每个子面映射整个新贴图。 */
    public static ObjectList<QuadMeshes.Face> quadrants(QuadMeshes.Face input, MaterialInfo topLeft, MaterialInfo topRight,
                                                        MaterialInfo bottomLeft, MaterialInfo bottomRight) {
        return ObjectList.of(
                patch(input, 0, 0, 0.5f, 0.5f, topLeft),
                patch(input, 0.5f, 0, 1, 0.5f, topRight),
                patch(input, 0, 0.5f, 0.5f, 1, bottomLeft),
                patch(input, 0.5f, 0.5f, 1, 1, bottomRight));
    }

    /** 参数域从顶点零出发，横轴沿零至三，纵轴沿零至一。 */
    public static QuadMeshes.Face patch(QuadMeshes.Face input, float u0, float v0, float u1, float v1, MaterialInfo material) {
        if (!(u0 >= 0 && u1 <= 1 && u0 < u1 && v0 >= 0 && v1 <= 1 && v0 < v1)) {
            throw new IllegalArgumentException("子面范围须在零至一之间");
        }
        QuadView source = QuadView.of(input.quad(), input.cullFace());
        var output = new MutableQuadView(source);
        var a = new Vector3f();
        var b = new Vector3f();
        var scratch = new Vector3f();
        boolean normals = (source.packedNormal(0) | source.packedNormal(1) | source.packedNormal(2) | source.packedNormal(3)) != 0;
        for (int vertex = 0; vertex < QuadView.VERTEX_COUNT; vertex++) {
            float u = vertex < 2 ? u0 : u1;
            float v = vertex == 0 || vertex == 3 ? v0 : v1;
            a.set(source.position(0)).lerp(source.position(3), u);
            b.set(source.position(1)).lerp(source.position(2), u);
            output.position(vertex, a.lerp(b, v));
            if (normals) {
                QuadGeometry.vertexNormal(source, 0, a).lerp(QuadGeometry.vertexNormal(source, 3, scratch), u);
                QuadGeometry.vertexNormal(source, 1, b).lerp(QuadGeometry.vertexNormal(source, 2, scratch), u);
                a.lerp(b, v);
                if (a.lengthSquared() > 0) output.normal(vertex, a.normalize());
                else output.packedNormal(vertex, 0);
            }
        }
        QuadGeometry.interpolate(source, output, source.lightFace().getAxis());
        output.material(material);
        var sprite = material.sprite();
        output.uv(0, sprite.getU0(), sprite.getV0()).uv(1, sprite.getU0(), sprite.getV1())
                .uv(2, sprite.getU1(), sprite.getV1()).uv(3, sprite.getU1(), sprite.getV0());
        return new QuadMeshes.Face(output.bake(), output.cullFace());
    }

    /** 两条相邻边及夹角按三个位存储，可作为材质数组索引。 */
    public static int connectionMask(boolean horizontal, boolean vertical, boolean diagonal) {
        return (horizontal ? 1 : 0) | (vertical ? 2 : 0) | (horizontal && vertical && diagonal ? 4 : 0);
    }
}
