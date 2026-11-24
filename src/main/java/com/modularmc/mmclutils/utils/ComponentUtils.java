package com.modularmc.mmclutils.utils;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;

/** 组合原版文本组件，保留子组件的样式和翻译信息。 */
public final class ComponentUtils {

    private ComponentUtils() {}

    public static MutableComponent join(Iterable<? extends Component> components, Component separator) {
        Iterator<? extends Component> iterator = components.iterator();
        MutableComponent result = Component.empty();
        if (iterator.hasNext()) result.append(iterator.next());
        while (iterator.hasNext()) result.append(separator).append(iterator.next());
        return result;
    }

    public static Collector<Component, ?, MutableComponent> joining(Component separator) {
        return Collector.of(
                ArrayList<Component>::new,
                List::add,
                (left, right) -> {
                    left.addAll(right);
                    return left;
                },
                parts -> join(parts, separator));
    }

    public static UnaryOperator<Style> cyclingColor(long tick, int ticksPerColor, int... colors) {
        if (ticksPerColor < 1 || colors.length == 0) throw new IllegalArgumentException("颜色序列不能为空，且每种颜色至少持续一个 Tick");
        int color = colors[Math.floorMod(Math.floorDiv(tick, ticksPerColor), colors.length)];
        return style -> style.withColor(color & 0xffffff);
    }

    public static UnaryOperator<Style> rainbow(double turns) {
        int color = ColorUtils.fromHsl((float) (turns - Math.floor(turns)), 1, 0.55f, 255);
        return style -> style.withColor(color & 0xffffff);
    }

    /** 按 Unicode 码点着色，保留非 BMP 字符。 */
    public static MutableComponent gradient(String text, int... colors) {
        if (colors.length == 0) throw new IllegalArgumentException("文本渐变至少需要一种颜色");
        var result = Component.empty();
        int last = Math.max(1, text.codePointCount(0, text.length()) - 1);
        for (int offset = 0, index = 0; offset < text.length(); index++) {
            int codePoint = text.codePointAt(offset);
            int end = offset + Character.charCount(codePoint);
            int color = ColorUtils.gradient(colors, (float) index / last);
            result.append(Component.literal(text.substring(offset, end)).withColor(color & 0xffffff));
            offset = end;
        }
        return result;
    }
}
