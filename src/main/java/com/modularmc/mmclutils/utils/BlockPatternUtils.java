package com.modularmc.mmclutils.utils;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.regex.Pattern;

/** 解析并匹配可复用的正则表达式配置规则。 */
public final class BlockPatternUtils {

    private BlockPatternUtils() {}

    /** 一条方块标识的正则规则及其倍率。 */
    public record MultiplierEntry(Pattern pattern, double value) {}

    /** 编译非空的方块标识正则，返回可反复匹配的不可变列表。无效正则会抛出异常。 */
    public static List<Pattern> precompilePatterns(List<String> textPatterns) {
        List<Pattern> patterns = new ObjectArrayList<>(textPatterns.size());
        for (String entry : textPatterns) {
            if (entry.isBlank()) {
                throw new IllegalArgumentException("方块黑名单的正则表达式不能为空");
            }
            patterns.add(Pattern.compile(entry));
        }
        return List.copyOf(patterns);
    }

    /** 把格式为 {@code 表达式=倍率} 的配置解析为不可变规则列表。倍率接受表达式，结果须为有限正数。 */
    public static List<MultiplierEntry> precompileMultipliers(List<String> textMultipliers) {
        List<MultiplierEntry> entries = new ObjectArrayList<>(textMultipliers.size());
        for (String entry : textMultipliers) {
            int separator = entry.lastIndexOf('=');
            if (separator <= 0 || separator >= entry.length() - 1) {
                throw new IllegalArgumentException("方块倍率须使用“表达式=倍率”格式，实际值为 " + entry);
            }
            String patternText = entry.substring(0, separator).trim();
            String valueText = entry.substring(separator + 1).trim();
            if (patternText.isEmpty()) {
                throw new IllegalArgumentException("方块倍率的正则表达式不能为空，实际值为 " + entry);
            }
            double value = NumberUtils.parseDouble(valueText)
                    .orElseThrow(() -> new IllegalArgumentException("无法解析方块倍率 " + entry));
            if (value <= 0.0D) {
                throw new IllegalArgumentException("方块倍率须为有限正数，实际值为 " + entry);
            }
            entries.add(new MultiplierEntry(Pattern.compile(patternText), value));
        }
        return List.copyOf(entries);
    }

    /** 逐项配对正则和倍率。两个数组长度须相同，倍率须为有限正数。 */
    public static List<MultiplierEntry> precompileMultipliers(String[] patterns, double[] values) {
        if (patterns.length != values.length) {
            throw new IllegalArgumentException("方块倍率的表达式和数值须一一对应");
        }
        List<MultiplierEntry> entries = new ObjectArrayList<>(patterns.length);
        for (int index = 0; index < patterns.length; index++) {
            String patternText = patterns[index];
            if (patternText == null || patternText.isBlank()) {
                throw new IllegalArgumentException("方块倍率的正则表达式不能为空");
            }
            double value = values[index];
            if (!Double.isFinite(value) || value <= 0.0D) {
                throw new IllegalArgumentException("方块倍率须为有限正数");
            }
            entries.add(new MultiplierEntry(Pattern.compile(patternText), value));
        }
        return List.copyOf(entries);
    }

    /** 判断完整方块标识是否被任一正则完整匹配。 */
    public static boolean isBlockBlacklisted(String blockId, List<Pattern> blacklist) {
        for (Pattern pattern : blacklist) {
            if (pattern.matcher(blockId).matches()) {
                return true;
            }
        }
        return false;
    }

    /** 返回匹配规则中的最大倍率，基础倍率为一。 */
    public static double getMultiplierForBlock(String blockId, List<MultiplierEntry> multipliers) {
        double maxMultiplier = 1.0D;
        for (MultiplierEntry entry : multipliers) {
            if (entry.pattern().matcher(blockId).matches()) {
                maxMultiplier = Math.max(maxMultiplier, entry.value());
            }
        }
        return maxMultiplier;
    }
}
