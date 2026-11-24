package com.modularmc.mmclutils.client.texture;

import com.modularmc.mmclutils.client.model.QuadView;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/** 用图集归一化 UV 反查贴图。图集重载后须用新贴图重建索引。 */
public final class SpriteLookup {

    private static final int MAX_DEPTH = 12;
    private final Node root = new Node(0, 0, 1, 0);
    private final TextureAtlasSprite missing;

    /** 在当前图集上建立查询索引。资源重载后，须使用新图集重新创建。 */
    public SpriteLookup(TextureAtlas atlas) {
        this(atlas.getTextures().values(), atlas.missingSprite());
    }

    /** 以给定的精灵集合建立索引，未命中时返回指定的缺失纹理。 */
    public SpriteLookup(Collection<TextureAtlasSprite> sprites, TextureAtlasSprite missing) {
        this.missing = missing;
        sprites.forEach(root::add);
    }

    /** 用四个顶点的 UV 重心定位贴图。 */
    public TextureAtlasSprite find(QuadView quad) {
        return find((quad.u(0) + quad.u(1) + quad.u(2) + quad.u(3)) * 0.25f,
                (quad.v(0) + quad.v(1) + quad.v(2) + quad.v(3)) * 0.25f);
    }

    /** 使用图集 UV 查找精灵。坐标越界或落在图集空白处时返回缺失纹理。 */
    public TextureAtlasSprite find(float u, float v) {
        if (!(u >= 0 && u < 1 && v >= 0 && v < 1)) return missing;
        TextureAtlasSprite sprite = root.find(u, v);
        return sprite == null ? missing : sprite;
    }

    private static final class Node {

        private final float x;
        private final float y;
        private final float size;
        private final int depth;
        private final ObjectArrayList<TextureAtlasSprite> sprites = new ObjectArrayList<>();
        private final Node[] children = new Node[4];

        private Node(float x, float y, float size, int depth) {
            this.x = x;
            this.y = y;
            this.size = size;
            this.depth = depth;
        }

        private void add(TextureAtlasSprite sprite) {
            float half = size * 0.5f, midU = x + half, midV = y + half;
            boolean crossesU = sprite.getU0() < midU && sprite.getU1() > midU;
            boolean crossesV = sprite.getV0() < midV && sprite.getV1() > midV;
            if (depth == MAX_DEPTH || crossesU || crossesV) {
                sprites.add(sprite);
                return;
            }
            int u = sprite.getU0() >= midU ? 1 : 0, v = sprite.getV0() >= midV ? 1 : 0;
            int index = u | (v << 1);
            if (children[index] == null) children[index] = new Node(x + u * half, y + v * half, half, depth + 1);
            children[index].add(sprite);
        }

        private @Nullable TextureAtlasSprite find(float u, float v) {
            for (TextureAtlasSprite sprite : sprites) {
                if (u >= sprite.getU0() && u < sprite.getU1() && v >= sprite.getV0() && v < sprite.getV1()) return sprite;
            }
            float half = size * 0.5f;
            int index = (u >= x + half ? 1 : 0) | (v >= y + half ? 2 : 0);
            Node child = children[index];
            return child == null ? null : child.find(u, v);
        }
    }
}
