package com.modularmc.mmclutils.client.render.effect;

import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * 从粒子图集读取并缓存各帧的 UV，资源重载后重新解析。
 */
public final class MoteSprite {

    /** 最多从精灵集合取样的帧数。 */
    public static final int MAX_FRAMES = 64;

    static int generation;
    private final SpriteSet set;
    private int seen = -1;
    private float[] uvs = new float[0];
    private int frames;
    int tableRow = -1;

    private MoteSprite(SpriteSet set) {
        this.set = set;
    }

    /**
     * 包装粒子工厂收到的精灵集合。首次绘制时才读取 UV。
     *
     * @param set 粒子图集的精灵集合，首次生成光点前须完成原版绑定
     * @return 可在多个光点样式中复用的精灵序列
     */
    public static MoteSprite of(SpriteSet set) {
        return new MoteSprite(set);
    }

    private void resolve() {
        if (seen == generation) return;
        var list = new ObjectArrayList<TextureAtlasSprite>();
        for (int i = 0; i < MAX_FRAMES; i++) {
            TextureAtlasSprite s = set.get(i, MAX_FRAMES - 1);
            if (!list.contains(s)) list.add(s);
        }
        frames = list.size();
        uvs = new float[frames * 4];
        for (int i = 0; i < frames; i++) {
            var s = list.get(i);
            uvs[i * 4] = s.getU0();
            uvs[i * 4 + 1] = s.getV0();
            uvs[i * 4 + 2] = s.getU1();
            uvs[i * 4 + 3] = s.getV1();
        }
        tableRow = -1;
        seen = generation;
    }

    int frames() {
        resolve();
        return frames;
    }

    float[] uvs() {
        resolve();
        return uvs;
    }
}
