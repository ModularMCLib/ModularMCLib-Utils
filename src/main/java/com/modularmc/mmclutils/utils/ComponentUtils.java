package com.modularmc.mmclutils.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.properties.Property;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.NullMarked;

import java.util.Iterator;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;

/** 组合原版文本组件，保留子组件的样式和翻译信息。 */
@NullMarked
public final class ComponentUtils {

    private ComponentUtils() {}

    /**
     * 将方块状态属性及其原版序列化值组成一个可显示的组件。布尔值使用绿色和红色区分。
     *
     * @param property 要显示的属性
     * @param value    属于该属性的值
     * @return 属性名称和带样式的值
     */
    public static MutableComponent propertyText(Property<?> property, Comparable<?> value) {
        MutableComponent text = Component.literal(Util.getPropertyName(property, value));
        if (value instanceof Boolean flag) text.withStyle(flag ? ChatFormatting.GREEN : ChatFormatting.RED);
        return Component.literal(property.getName()).append(": ").append(text);
    }

    /** 将长整数按紧凑数值格式封装为字面组件。 */
    public static MutableComponent numberText(long value) {
        return Component.literal(NumberUtils.format(value));
    }

    /** 将浮点数按紧凑数值格式封装为字面组件。 */
    public static MutableComponent numberText(double value) {
        return Component.literal(NumberUtils.format(value));
    }

    /** 用翻译键显示百分数，数字部分最多两位小数。 */
    public static MutableComponent percentText(String translationKey, double percent) {
        return Component.translatable(translationKey, NumberUtils.formatTwoDecimals(percent));
    }

    /** 创建可继续添加行的列表，首行使用字面文本。 */
    public static List<MutableComponent> literalLine(String text) {
        var lines = new ObjectArrayList<MutableComponent>(1);
        lines.add(Component.literal(text));
        return lines;
    }

    /** 创建只含一行长整数文本的可修改列表。 */
    public static List<MutableComponent> literalLine(long value) {
        return literalLine(Long.toString(value));
    }

    /** 创建只含一行浮点数文本的可修改列表。 */
    public static List<MutableComponent> literalLine(double value) {
        return literalLine(Double.toString(value));
    }

    /** 将各行解析为纯文本并以换行连接。 */
    public static String plainText(Iterable<? extends Component> lines) {
        StringJoiner text = new StringJoiner("\n");
        for (Component line : lines) text.add(line.getString());
        return text.toString();
    }

    /** 解析一行整数。空列表视作零，多行或无效数值抛出 NumberFormatException。 */
    public static int parseInt(List<? extends Component> lines) {
        return lines.isEmpty() ? 0 : Integer.parseInt(singleLineText(lines));
    }

    /** 解析一行浮点数。空列表视作零，多行或无效数值抛出 NumberFormatException。 */
    public static double parseDouble(List<? extends Component> lines) {
        return lines.isEmpty() ? 0 : Double.parseDouble(singleLineText(lines));
    }

    private static String singleLineText(List<? extends Component> lines) {
        if (lines.size() != 1) throw new NumberFormatException("数值文本须为单行");
        return lines.getFirst().getString();
    }

    /** 把首行接到目标末行，其余行逐行添加。目标允许为空，待追加的行由独立列表提供。 */
    public static void appendLines(List<MutableComponent> target, List<? extends Component> lines) {
        Iterator<? extends Component> remaining = lines.iterator();
        if (!remaining.hasNext()) return;
        if (target.isEmpty()) target.add(Component.empty());
        target.getLast().append(remaining.next());
        while (remaining.hasNext()) target.add(Component.empty().append(remaining.next()));
    }

    /** 两个组件都有内容时插入分隔符。 */
    public static MutableComponent appendSeparated(MutableComponent target, Component addition, Component separator) {
        if (!hasContent(addition)) return target;
        if (hasContent(target)) target.append(separator);
        return target.append(addition);
    }

    private static boolean hasContent(Component component) {
        return component.getContents() != PlainTextContents.EMPTY || !component.getSiblings().isEmpty();
    }

    /** 以带引号的条目预览列表。超过五项时保留首尾各两项。 */
    public static MutableComponent previewList(List<String> entries, Style entryStyle) {
        List<Component> preview = new ObjectArrayList<>(Math.min(5, entries.size()));
        if (entries.size() <= 5) {
            for (String entry : entries) preview.add(quoted(entry, entryStyle));
        } else {
            preview.add(quoted(entries.getFirst(), entryStyle));
            preview.add(quoted(entries.get(1), entryStyle));
            preview.add(Component.literal("…"));
            preview.add(quoted(entries.get(entries.size() - 2), entryStyle));
            preview.add(quoted(entries.getLast(), entryStyle));
        }
        return Component.literal("[").append(join(preview, Component.literal(", "))).append("]");
    }

    private static MutableComponent quoted(String entry, Style style) {
        return Component.literal('"' + entry + '"').withStyle(style);
    }

    /** 按迭代顺序拼接组件，在相邻项之间插入分隔符。各子组件的翻译和样式得以保留。 */
    public static MutableComponent join(Iterable<? extends Component> components, Component separator) {
        Iterator<? extends Component> iterator = components.iterator();
        MutableComponent result = Component.empty();
        if (iterator.hasNext()) result.append(iterator.next());
        while (iterator.hasNext()) result.append(separator).append(iterator.next());
        return result;
    }

    /** 创建与 {@link #join} 一致的流收集器，保留来源的相遇顺序。 */
    public static Collector<Component, ?, MutableComponent> joining(Component separator) {
        return Collector.of(
                ObjectArrayList<Component>::new,
                List::add,
                (left, right) -> {
                    left.addAll(right);
                    return left;
                },
                parts -> join(parts, separator));
    }

    /** 按 Tick 从色板选定颜色。每种颜色须持续至少一个 Tick，色板须包含至少一种颜色。 */
    public static UnaryOperator<Style> cyclingColor(long tick, int ticksPerColor, int... colors) {
        int color = colors[cycleIndex(tick, ticksPerColor, colors.length)];
        return style -> style.withColor(color & 0xffffff);
    }

    /** 按 Unicode 码点循环分配色板，起点随调用方提供的 Tick 推移。 */
    public static MutableComponent scrollingColor(String text, long tick, int ticksPerColor, int... colors) {
        int index = (colors.length - cycleIndex(tick, ticksPerColor, colors.length)) % colors.length;
        MutableComponent result = Component.empty();
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            int end = offset + Character.charCount(codePoint);
            result.append(Component.literal(text.substring(offset, end)).withColor(colors[index] & 0xffffff));
            offset = end;
            if (++index == colors.length) index = 0;
        }
        return result;
    }

    private static int cycleIndex(long tick, int ticksPerColor, int colorCount) {
        if (ticksPerColor < 1 || colorCount == 0) throw new IllegalArgumentException("颜色序列不能为空，且每种颜色至少持续一个 Tick");
        return Math.floorMod(Math.floorDiv(tick, ticksPerColor), colorCount);
    }

    /** 按圈数选取循环色相，返回只修改文本颜色的样式变换。 */
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
