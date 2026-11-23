package com.modularmc.mmclutils.client.render.effect;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.jspecify.annotations.Nullable;

/**
 * 光点样式可选择带亮芯的像素光晕，或由调用方提供的粒子精灵。
 */
@OnlyIn(Dist.CLIENT)
public record MoteLook(@Nullable MoteSprite sprite, boolean animated, float rollSpeed, float core) {

    public static final MoteLook GLOW = new MoteLook(null, false, 0, 0.3F);

    public static MoteLook glow(float core) {
        return new MoteLook(null, false, 0, core);
    }

    public static MoteLook sprite(MoteSprite sprite) {
        return new MoteLook(sprite, false, 0, 0);
    }

    public static MoteLook animated(MoteSprite sprite) {
        return new MoteLook(sprite, true, 0, 0);
    }

    public static MoteLook spinning(MoteSprite sprite, float rollSpeed) {
        return new MoteLook(sprite, false, rollSpeed, 0);
    }
}
