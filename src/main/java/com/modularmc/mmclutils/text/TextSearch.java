package com.modularmc.mmclutils.text;

import java.lang.Character.UnicodeScript;
import java.util.regex.Pattern;

/** 标准化搜索文本，支持子序列和调用者提供的完整正则匹配。 */
public final class TextSearch {

    private TextSearch() {}

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
