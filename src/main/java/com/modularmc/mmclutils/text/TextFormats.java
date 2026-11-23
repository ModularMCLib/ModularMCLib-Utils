package com.modularmc.mmclutils.text;

import org.jspecify.annotations.Nullable;

/** 按分隔符拆分文本，并转换十进制单字数字。 */
public final class TextFormats {

    private static final String CHINESE_DIGITS = "零一二三四五六七八九";

    private TextFormats() {}

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
}
