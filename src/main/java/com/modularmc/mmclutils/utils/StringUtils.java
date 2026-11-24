package com.modularmc.mmclutils.utils;

import org.jspecify.annotations.Nullable;

import java.lang.Character.UnicodeScript;
import java.util.regex.Pattern;

/** 按分隔符拆分文本、转换十进制单字数字，以及标准化和匹配搜索词。 */
public final class StringUtils {

    private static final String CHINESE_DIGITS = "零一二三四五六七八九";

    private StringUtils() {}

    public static String chineseDigit(int digit) {
        if (digit < 0 || digit >= CHINESE_DIGITS.length()) throw new IllegalArgumentException("数字须在零至九之间");
        return String.valueOf(CHINESE_DIGITS.charAt(digit));
    }

    public static Parts splitFirst(String value, char delimiter) {
        return split(value, value.indexOf(delimiter));
    }

    public static Parts splitLast(String value, char delimiter) {
        return split(value, value.lastIndexOf(delimiter));
    }

    private static Parts split(String value, int index) {
        return index < 0 ? new Parts(value, null) : new Parts(value.substring(0, index), value.substring(index + 1));
    }

    /** 缺少分隔符时后段为空值，分隔符位于边缘时对应一段为空字符串。 */
    public record Parts(String first, @Nullable String second) {}

    public static boolean isCjk(int codePoint) {
        return UnicodeScript.of(codePoint) == UnicodeScript.HAN;
    }

    public static String normalize(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length();) {
            int point = text.codePointAt(i);
            if (Character.isLetterOrDigit(point) || isCjk(point)) result.appendCodePoint(Character.toLowerCase(point));
            i += Character.charCount(point);
        }
        return result.toString();
    }

    public static boolean matches(String text, String query) {
        return !text.isEmpty() && matchesNormalized(text, normalize(query));
    }

    /** 查询词已标准化时使用，可复用于多个候选。 */
    public static boolean matchesNormalized(String text, String normalizedQuery) {
        if (normalizedQuery.isEmpty()) return true;
        String normalizedText = normalize(text);
        int wanted = 0;
        for (int i = 0; i < normalizedText.length();) {
            int point = normalizedText.codePointAt(i);
            if (point == normalizedQuery.codePointAt(wanted)) {
                wanted += Character.charCount(point);
                if (wanted == normalizedQuery.length()) return true;
            }
            i += Character.charCount(point);
        }
        return false;
    }

    public static boolean matchesAny(String text, Pattern... patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).matches()) return true;
        }
        return false;
    }
}
