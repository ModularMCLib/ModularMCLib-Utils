package com.modularmc.mmclutils.client.gui;

import com.modularmc.mmclutils.color.ColorMath;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.function.UnaryOperator;

/** 生成提示文本的渐变和周期颜色。时间由绘制调用方传入，可与界面动画复用同一时钟。 */
public final class Tooltips {

    private Tooltips() {}

    public static UnaryOperator<Style> cyclingColor(long tick, int ticksPerColor, int... colors) {
        if (ticksPerColor < 1 || colors.length == 0) throw new IllegalArgumentException("提示颜色和周期须有效");
        int color = colors[Math.floorMod(Math.floorDiv(tick, ticksPerColor), colors.length)];
        return style -> style.withColor(color & 0xffffff);
    }

    public static UnaryOperator<Style> rainbow(double turns) {
        int color = ColorMath.fromHsl((float) (turns - Math.floor(turns)), 1, 0.55f, 255);
        return style -> style.withColor(color & 0xffffff);
    }

    /** 按 Unicode 码点着色，保留非 BMP 字符。 */
    public static MutableComponent gradient(String text, int... colors) {
        if (colors.length == 0) throw new IllegalArgumentException("提示渐变至少需要一种颜色");
        var result = Component.empty();
        int last = Math.max(1, text.codePointCount(0, text.length()) - 1);
        for (int offset = 0, index = 0; offset < text.length(); index++) {
            int codePoint = text.codePointAt(offset);
            int end = offset + Character.charCount(codePoint);
            int color = ColorMath.gradient(colors, (float) index / last);
            result.append(Component.literal(text.substring(offset, end)).withColor(color & 0xffffff));
            offset = end;
        }
        return result;
    }
}
