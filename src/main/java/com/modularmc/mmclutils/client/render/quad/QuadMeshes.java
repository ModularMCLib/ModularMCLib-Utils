package com.modularmc.mmclutils.client.render.quad;

import com.modularmc.mmclutils.client.render.texture.TextureCoordinates;

import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/** 按原版剔除方向组织烘焙面，并提供可复用的批量变换入口。 */
public final class QuadMeshes {

    private QuadMeshes() {}

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
        TextureCoordinates.bake(TextureCoordinates.lockToFace(quad, face), material.sprite());
        return new Face(quad.bake(), onEdge ? face : null);
    }

    public static ObjectList<Face> box(AABB bounds, MaterialInfo material) {
        var faces = new ObjectArrayList<Face>(6);
        for (Direction direction : Direction.values()) faces.add(boxFace(bounds, direction, material));
        return new ObjectImmutableList<>(faces);
    }

    /** 烘焙四边形及其剔除分组。空方向表示通用面。 */
    public record Face(BakedQuad quad, @Nullable Direction cullFace) {}
}
