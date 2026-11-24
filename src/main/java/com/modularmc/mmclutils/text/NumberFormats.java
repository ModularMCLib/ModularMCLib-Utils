package com.modularmc.mmclutils.text;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * 用紧凑的十进制单位显示整数。
 *
 * <p>
 * 每增加三个数量级切换一次单位，超出 {@code Att} 时采用科学计数法。
 * </p>
 */
public final class NumberFormats {

    private static final BigInteger UNIT_BASE = BigInteger.valueOf(1_000L);
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final String[] COMPACT_UNITS = {
            "", "K", "M", "G", "T", "P", "E", "Z", "Y", "B", "N", "D", "C", "S", "O", "Q", "X", "W", "V",
            "U", "Tt", "Gt", "Mt", "St", "Ot", "Nt", "Dt", "Ct", "Lt", "Kt", "Jt", "It", "Ht", "Gtt", "Ett",
            "Dtt", "Ctt", "Btt", "Att"
    };
    private static final BigDecimal SCIENTIFIC_THRESHOLD = new BigDecimal(UNIT_BASE.pow(COMPACT_UNITS.length));

    private NumberFormats() {}

    /**
     * 格式化非负时长，根据大小选择微秒、毫秒或秒。
     *
     * @param nanos 以 {@link System#nanoTime()} 测得的时长
     * @return 保留一位小数且附带单位的时长
     */
    public static String formatDuration(long nanos) {
        if (nanos < 0L) {
            throw new IllegalArgumentException("A Trinity duration must not be negative");
        }
        if (nanos < NANOS_PER_MILLISECOND) {
            return formatDuration(nanos, 3, " μs");
        }
        if (nanos < NANOS_PER_SECOND) {
            return formatDuration(nanos, 6, " ms");
        }
        return formatDuration(nanos, 9, " s");
    }

    private static String formatDuration(long nanos, int scale, String unit) {
        BigDecimal rounded = BigDecimal.valueOf(nanos, scale).setScale(1, RoundingMode.HALF_EVEN);
        if (nanos > 0L && rounded.signum() == 0) {
            return "<0.1" + unit;
        }
        return rounded.stripTrailingZeros().toPlainString() + unit;
    }

    /**
     * 解析并格式化十进制有符号整数。
     *
     * @param value 允许含首尾空白的十进制整数
     * @return 紧凑的显示文本
     */
    public static String format(String value) {
        if (value.isBlank()) {
            return "0";
        }
        return format(new BigInteger(value.trim()));
    }

    /**
     * 使用整数运算格式化有符号 {@code long}。
     *
     * @param value 待格式化数量
     * @return 紧凑的显示文本
     */
    public static String format(long value) {
        return format(BigInteger.valueOf(value));
    }

    /**
     * 格式化任意精度有符号整数。
     *
     * @param value 待格式化数量
     * @return 紧凑的显示文本
     */
    public static String format(BigInteger value) {
        return formatParts(value).text();
    }

    /** 格式化以显示单位表示的精确数量，支持带小数的流体量。 */
    public static String format(BigDecimal value) {
        return formatParts(value).text();
    }

    /**
     * 格式化 {@code long}，将数字和单位分开以支持各自的提示文本样式。
     *
     * @param value 待格式化数量
     * @return 数字文本和单位
     */
    public static FormattedAmount formatParts(long value) {
        return formatParts(BigInteger.valueOf(value));
    }

    /**
     * 格式化任意精度整数，并单独保留单位后缀。
     *
     * @param value 待格式化数量
     * @return 数字文本和单位
     */
    public static FormattedAmount formatParts(BigInteger value) {
        return formatParts(new BigDecimal(value));
    }

    private static FormattedAmount formatParts(BigDecimal value) {
        if (value.signum() == 0) {
            return new FormattedAmount("0", "");
        }

        BigDecimal absoluteAmount = value.abs();
        if (absoluteAmount.compareTo(SCIENTIFIC_THRESHOLD) >= 0) {
            return new FormattedAmount(formatScientific(value), "");
        }

        int unitIndex = Math.max(0, (absoluteAmount.precision() - absoluteAmount.scale() - 1) / 3);
        BigDecimal scaledAmount = absoluteAmount.movePointLeft(unitIndex * 3);
        String digits = compactFormat().format(scaledAmount);
        if (value.signum() < 0) {
            digits = "-" + digits;
        }
        return new FormattedAmount(digits, COMPACT_UNITS[unitIndex]);
    }

    private static String formatScientific(BigDecimal amount) {
        DecimalFormat format = decimalFormat("0.00E00");
        return format.format(amount);
    }

    private static DecimalFormat compactFormat() {
        return decimalFormat("#,##0.#");
    }

    private static DecimalFormat decimalFormat(String pattern) {
        DecimalFormat format = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.ROOT));
        format.setRoundingMode(RoundingMode.HALF_EVEN);
        return format;
    }

    /**
     * 数字和单位分开存放，便于分别应用样式。
     *
     * @param digits 格式化后的数字，负数带符号
     * @param unit   单位后缀，未缩放或使用科学计数法时为空字符串
     */
    public record FormattedAmount(String digits, String unit) {

        /**
         * 拼接数字与单位，供纯文本控件显示。
         *
         * @return 完整的格式化文本
         */
        public String text() {
            return this.digits + this.unit;
        }
    }
}
