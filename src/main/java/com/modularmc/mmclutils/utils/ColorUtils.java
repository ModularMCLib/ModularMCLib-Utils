package com.modularmc.mmclutils.utils;

import net.minecraft.util.FastColor.ABGR32;
import net.minecraft.util.FastColor.ARGB32;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.material.MapColor;

import it.unimi.dsi.fastutil.ints.IntIntPair;

/** 颜色分量、颜色空间和分段渐变。颜色按 ARGB 打包。 */
public final class ColorUtils {

    private static final DyeColor[] DYE_COLORS = DyeColor.values();

    private ColorUtils() {}

    /** 将给定透明度和低 24 位的 RGB 合成为 ARGB。 */
    public static int withAlpha(int rgb, int alpha) {
        return ARGB32.color(alpha, rgb);
    }

    /** 输入只含 RGB 分量时补上透明度，已有非零透明度的颜色直接返回。 */
    public static int withDefaultAlpha(int color, int alpha) {
        return ARGB32.alpha(color) == 0 ? withAlpha(color, alpha) : color;
    }

    /** 以 0～1 的比例逐通道插值 ARGB，并四舍五入到整数分量。 */
    public static int interpolate(int from, int to, float amount) {
        return ARGB32.color(
                Math.round(Mth.lerp(amount, ARGB32.alpha(from), ARGB32.alpha(to))),
                Math.round(Mth.lerp(amount, ARGB32.red(from), ARGB32.red(to))),
                Math.round(Mth.lerp(amount, ARGB32.green(from), ARGB32.green(to))),
                Math.round(Mth.lerp(amount, ARGB32.blue(from), ARGB32.blue(to))));
    }

    /** 交换红色与蓝色通道，保持透明度和绿色通道。 */
    public static int swapRedBlue(int argb) {
        return ABGR32.fromArgb32(argb);
    }

    /** 将最高字节的透明度移到最低字节。 */
    public static int argbToRgba(int argb) {
        return Integer.rotateLeft(argb, 8);
    }

    /** 返回归一化到 0～1 的红、绿、蓝三个分量。 */
    public static float[] rgb(int argb) {
        return new float[] { ARGB32.red(argb) / 255f, ARGB32.green(argb) / 255f, ARGB32.blue(argb) / 255f };
    }

    /** 两个 RGBA 值中位置相同的分量相乘，结果仍按 RGBA 排列。 */
    public static int multiplyRgba(int first, int second) {
        return ARGB32.multiply(first, second);
    }

    /** 以 RGB 分量的平方距离匹配最接近的染料文字颜色。 */
    public static DyeColor nearestDyeColor(int rgb) {
        DyeColor nearest = DYE_COLORS[0];
        int bestDistance = Integer.MAX_VALUE;
        for (DyeColor color : DYE_COLORS) {
            int distance = rgbDistanceSquared(rgb, color.getTextColor());
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = color;
            }
        }
        return nearest;
    }

    /** 在原版地图的六位色表中匹配最接近的可见色。 */
    public static MapColor nearestMapColor(int rgb) {
        MapColor nearest = MapColor.NONE;
        int bestDistance = Integer.MAX_VALUE;
        for (int id = 1; id < 64; id++) {
            MapColor color = MapColor.byId(id);
            if (color == MapColor.NONE) continue;
            int distance = rgbDistanceSquared(rgb, swapRedBlue(color.calculateRGBColor(MapColor.Brightness.NORMAL)));
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = color;
            }
        }
        return nearest;
    }

    private static int rgbDistanceSquared(int first, int second) {
        int red = ARGB32.red(first) - ARGB32.red(second);
        int green = ARGB32.green(first) - ARGB32.green(second);
        int blue = ARGB32.blue(first) - ARGB32.blue(second);
        return red * red + green * green + blue * blue;
    }

    /** 按相同透明度填充两种 RGB 之间的均匀色阶，色阶数至少为二并包含两端颜色。 */
    public static int[] stepGradient(int from, int to, int alpha, int steps) {
        if (steps < 2) throw new IllegalArgumentException("渐变至少需要两个色阶");
        int[] result = new int[steps];
        for (int index = 0; index < steps; index++) {
            float amount = (float) index / (steps - 1);
            result[index] = ARGB32.color(alpha,
                    Math.round(Mth.lerp(amount, ARGB32.red(from), ARGB32.red(to))),
                    Math.round(Mth.lerp(amount, ARGB32.green(from), ARGB32.green(to))),
                    Math.round(Mth.lerp(amount, ARGB32.blue(from), ARGB32.blue(to))));
        }
        return result;
    }

    /** 从色板按归一化进度选出相邻色阶并插值。色板至少一个颜色。 */
    public static int gradient(int[] stops, float position) {
        if (stops.length == 0) throw new IllegalArgumentException("渐变至少需要一种颜色");
        float scaled = Mth.clamp(position, 0f, 1f) * (stops.length - 1);
        int first = (int) scaled;
        return first == stops.length - 1 ? stops[first] : interpolate(stops[first], stops[first + 1], scaled - first);
    }

    /** 将色相、饱和度和亮度转换成 ARGB。色相以一圈为 1。 */
    public static int fromHsl(float hue, float saturation, float lightness, int alpha) {
        float turn = hue - (float) Math.floor(hue);
        float sat = Mth.clamp(saturation, 0f, 1f);
        float light = Mth.clamp(lightness, 0f, 1f);
        float chroma = (1 - Math.abs(2 * light - 1)) * sat;
        return ARGB32.color(alpha, hslChannel(turn, chroma, light, 0), hslChannel(turn, chroma, light, 8),
                hslChannel(turn, chroma, light, 4));
    }

    /** 根据归一化 HSL 及指定透明度生成 ARGB。 */
    public static int fromHsl(Hsl hsl, int alpha) {
        return fromHsl(hsl.hue(), hsl.saturation(), hsl.lightness(), alpha);
    }

    /** 取得归一化的 HSL 分量。灰阶的色相与饱和度均为零。 */
    public static Hsl toHsl(int argb) {
        int red = ARGB32.red(argb);
        int green = ARGB32.green(argb);
        int blue = ARGB32.blue(argb);
        int maximum = Math.max(red, Math.max(green, blue));
        int minimum = Math.min(red, Math.min(green, blue));
        int chroma = maximum - minimum;
        float lightness = (maximum + minimum) / 510f;
        if (chroma == 0) return new Hsl(0, 0, lightness);

        float hue;
        if (maximum == red) {
            hue = (green - blue) / (float) chroma;
        } else if (maximum == green) {
            hue = 2 + (blue - red) / (float) chroma;
        } else {
            hue = 4 + (red - green) / (float) chroma;
        }
        if (hue < 0) hue += 6;
        float saturation = chroma / (255f - Math.abs(maximum + minimum - 255));
        return new Hsl(hue / 6, saturation, lightness);
    }

    /** 从原色的亮度减去和加上同一偏移量，依次返回暗色和亮色并保留透明度。 */
    public static IntIntPair lightnessGradient(int argb, float difference) {
        if (!(difference >= 0)) throw new IllegalArgumentException("亮度偏移量须为非负数");
        Hsl hsl = toHsl(argb);
        int alpha = ARGB32.alpha(argb);
        int dark = fromHsl(hsl.hue(), hsl.saturation(), hsl.lightness() - difference, alpha);
        int light = fromHsl(hsl.hue(), hsl.saturation(), hsl.lightness() + difference, alpha);
        return IntIntPair.of(dark, light);
    }

    private static int hslChannel(float hue, float chroma, float lightness, int offset) {
        float phase = (offset + 12 * hue) % 12;
        float weight = Math.max(-1, Math.min(Math.min(phase - 3, 9 - phase), 1));
        return Math.round(255 * (lightness - chroma * weight / 2));
    }

    /** 色相以圈为单位，饱和度与亮度使用 0～1。 */
    public record Hsl(float hue, float saturation, float lightness) {}
}
