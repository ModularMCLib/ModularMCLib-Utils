package com.modularmc.mmclutils.utils;

import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/** 颜色分量、颜色空间和分段渐变。颜色按 ARGB 打包。 */
public final class ColorUtils {

    private ColorUtils() {}

    public static int withAlpha(int rgb, int alpha) {
        return ARGB.color(alpha, rgb);
    }

    public static int interpolate(int from, int to, float amount) {
        return ARGB.color(
                Math.round(Mth.lerp(amount, ARGB.alpha(from), ARGB.alpha(to))),
                Math.round(Mth.lerp(amount, ARGB.red(from), ARGB.red(to))),
                Math.round(Mth.lerp(amount, ARGB.green(from), ARGB.green(to))),
                Math.round(Mth.lerp(amount, ARGB.blue(from), ARGB.blue(to))));
    }

    public static int swapRedBlue(int argb) {
        return ARGB.color(ARGB.alpha(argb), ARGB.blue(argb), ARGB.green(argb), ARGB.red(argb));
    }

    public static int[] stepGradient(int from, int to, int alpha, int steps) {
        if (steps < 2) throw new IllegalArgumentException("渐变至少需要两个色阶");
        int[] result = new int[steps];
        for (int index = 0; index < steps; index++) {
            float amount = (float) index / (steps - 1);
            result[index] = ARGB.color(alpha,
                    Math.round(Mth.lerp(amount, ARGB.red(from), ARGB.red(to))),
                    Math.round(Mth.lerp(amount, ARGB.green(from), ARGB.green(to))),
                    Math.round(Mth.lerp(amount, ARGB.blue(from), ARGB.blue(to))));
        }
        return result;
    }

    public static int gradient(int[] stops, float position) {
        if (stops.length == 0) throw new IllegalArgumentException("渐变至少需要一种颜色");
        float scaled = Mth.clamp(position, 0f, 1f) * (stops.length - 1);
        int first = (int) scaled;
        return first == stops.length - 1 ? stops[first] : interpolate(stops[first], stops[first + 1], scaled - first);
    }

    public static int fromHsl(float hue, float saturation, float lightness, int alpha) {
        float turn = hue - (float) Math.floor(hue);
        float sat = Mth.clamp(saturation, 0f, 1f);
        float light = Mth.clamp(lightness, 0f, 1f);
        float chroma = (1 - Math.abs(2 * light - 1)) * sat;
        return ARGB.color(alpha, hslChannel(turn, chroma, light, 0), hslChannel(turn, chroma, light, 8),
                hslChannel(turn, chroma, light, 4));
    }

    private static int hslChannel(float hue, float chroma, float lightness, int offset) {
        float phase = (offset + 12 * hue) % 12;
        float weight = Math.max(-1, Math.min(Math.min(phase - 3, 9 - phase), 1));
        return Math.round(255 * (lightness - chroma * weight / 2));
    }
}
