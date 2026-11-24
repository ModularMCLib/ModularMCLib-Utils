package com.modularmc.mmclutils.utils;

import org.jspecify.annotations.Nullable;

import java.lang.Character.UnicodeScript;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** 按分隔符拆分文本、转换十进制单字数字，以及标准化和匹配搜索词。 */
public final class StringUtils {

    private static final String CHINESE_DIGITS = "零一二三四五六七八九";
    private static final String SUPERSCRIPT_DIGITS = "⁰¹²³⁴⁵⁶⁷⁸⁹";
    private static final String SUBSCRIPT_DIGITS = "₀₁₂₃₄₅₆₇₈₉";
    private static final int[] ROMAN_VALUES = { 1_000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1 };
    private static final String[] ROMAN_SYMBOLS = { "M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I" };

    private StringUtils() {}

    public static String chineseDigit(int digit) {
        if (digit < 0 || digit >= CHINESE_DIGITS.length()) throw new IllegalArgumentException("数字须在零至九之间");
        return String.valueOf(CHINESE_DIGITS.charAt(digit));
    }

    /** 将 ASCII 数字替换为 Unicode 上标。 */
    public static String superscriptDigits(String text) {
        return replaceDigits(text, SUPERSCRIPT_DIGITS);
    }

    /** 将 ASCII 数字替换为 Unicode 下标。 */
    public static String subscriptDigits(String text) {
        return replaceDigits(text, SUBSCRIPT_DIGITS);
    }

    private static String replaceDigits(String text, String alphabet) {
        StringBuilder result = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            int digit = character - '0';
            result.append(digit >= 0 && digit <= 9 ? alphabet.charAt(digit) : character);
        }
        return result.toString();
    }

    /** 将非负整数写成罗马数字，零用空字符串表示。 */
    public static String romanNumeral(int value) {
        if (value < 0) throw new IllegalArgumentException("罗马数字仅接受非负整数");
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < ROMAN_VALUES.length && value > 0; index++) {
            int symbolValue = ROMAN_VALUES[index];
            result.append(ROMAN_SYMBOLS[index].repeat(value / symbolValue));
            value %= symbolValue;
        }
        return result.toString();
    }

    /** 在数字后附加英文序数后缀。 */
    public static String ordinal(int value) {
        int lastTwo = Math.abs(value % 100);
        String suffix = lastTwo >= 11 && lastTwo <= 13 ? "th" : switch (lastTwo % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return value + suffix;
    }

    /** 保留已有下划线，在大小写词边界及字母与数字边界补充分隔符。 */
    public static String toLowerUnderscore(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            result.append(Character.toLowerCase(current));
            if (index == text.length() - 1) break;

            char next = text.charAt(index + 1);
            if (current == '_' || next == '_') continue;
            if ((!Character.isUpperCase(current) && Character.isUpperCase(next)) || (Character.isDigit(current) != Character.isDigit(next))) {
                result.append('_');
            }
        }
        return result.toString();
    }

    /** 按 Unicode 码点判断是否包含大写字母。 */
    public static boolean hasUpperCase(String text) {
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if (Character.isUpperCase(codePoint)) return true;
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    /** 去掉分隔下划线并将后续词的首字符转为大写。 */
    public static String lowerUnderscoreToCamel(String text) {
        StringBuilder result = new StringBuilder(text.length());
        boolean startsWord = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '_') {
                startsWord = true;
            } else {
                result.append(startsWord ? Character.toUpperCase(current) : current);
                startsWord = false;
            }
        }
        return result.toString();
    }

    /** 按下划线分词，以空格连接首字母大写的词。 */
    public static String toTitleWords(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (String word : text.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            int first = word.codePointAt(0);
            result.appendCodePoint(Character.toTitleCase(first)).append(word, Character.charCount(first), word.length());
        }
        return result.toString();
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

    /** 依次按给定的正则表达式匹配完整文本。 */
    public static boolean matchesAnyRegex(String text, String... patterns) {
        for (String pattern : patterns) {
            if (Pattern.matches(pattern, text)) return true;
        }
        return false;
    }

    /** 按顺序为原文本的每个匹配使用一个替换串，支持 Matcher 的捕获组语法。 */
    public static String replaceMatches(String text, Pattern pattern, List<String> replacements) {
        var matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder(text.length());
        for (String replacement : replacements) {
            if (!matcher.find()) break;
            matcher.appendReplacement(result, replacement);
        }
        return matcher.appendTail(result).toString();
    }
}
