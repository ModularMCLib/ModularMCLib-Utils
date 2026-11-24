package com.modularmc.mmclutils.utils;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * 用紧凑的十进制单位显示数值，或解析带单位后缀的数值表达式。
 *
 * <p>
 * 每增加三个数量级切换一次单位，超出 {@code Att} 时采用科学计数法。
 * </p>
 */
@NullMarked
public final class NumberUtils {

    private static final BigInteger UNIT_BASE = BigInteger.valueOf(1_000L);
    private static final long NANOS_PER_MILLISECOND = 1_000_000L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final String[] COMPACT_UNITS = {
            "", "K", "M", "G", "T", "P", "E", "Z", "Y", "B", "N", "D", "C", "S", "O", "Q", "X", "W", "V",
            "U", "Tt", "Gt", "Mt", "St", "Ot", "Nt", "Dt", "Ct", "Lt", "Kt", "Jt", "It", "Ht", "Gtt", "Ett",
            "Dtt", "Ctt", "Btt", "Att"
    };
    private static final BigDecimal SCIENTIFIC_THRESHOLD = new BigDecimal(UNIT_BASE.pow(COMPACT_UNITS.length));

    private NumberUtils() {}

    /**
     * 格式化非负时长，根据大小选择微秒、毫秒或秒。
     *
     * @param nanos 以 {@link System#nanoTime()} 测得的时长
     * @return 保留一位小数且附带单位的时长
     */
    public static String formatDuration(long nanos) {
        if (nanos < 0L) {
            throw new IllegalArgumentException("时长须为非负数");
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

    /** 按紧凑单位显示浮点数，保留非有限值的标准文本。 */
    public static String format(double value) {
        return Double.isFinite(value) ? format(BigDecimal.valueOf(value)) : Double.toString(value);
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

    /** 千以下显示原值，千到百万之间使用 K，百万以上使用 M 并附紧凑数量。 */
    public static String formatThousandsOrMillions(long value) {
        if (value > -1_000 && value < 1_000) return Long.toString(value);
        boolean millions = value <= -1_000_000 || value >= 1_000_000;
        String digits = decimalFormat("#,##0.00").format(BigDecimal.valueOf(value, millions ? 6 : 3));
        return millions ? digits + "M (" + format(value) + ")" : digits + "K";
    }

    public static String formatGrouped(long value) {
        return NumberFormat.getNumberInstance(Locale.ROOT).format(value);
    }

    public static String formatGrouped(double value) {
        return NumberFormat.getNumberInstance(Locale.ROOT).format(value);
    }

    public static String formatGrouped(Number value) {
        return NumberFormat.getNumberInstance(Locale.ROOT).format(value);
    }

    /** 显示最多两位小数并保留千位分组。 */
    public static String formatTwoDecimals(double value) {
        return decimalFormat("#,##0.##").format(value);
    }

    /** 将百分数的数值部分固定为两位小数，百分号由调用方决定。 */
    public static String formatPercent(double percent) {
        return decimalFormat("#,##0.00").format(percent);
    }

    /** 数值的绝对值超过阈值时使用科学计数法。 */
    public static String formatWithScientificThreshold(BigInteger value, BigInteger threshold) {
        if (threshold.signum() < 0) throw new IllegalArgumentException("科学计数阈值须为非负数");
        return value.abs().compareTo(threshold) > 0 ? formatScientific(new BigDecimal(value)) : formatGrouped(value);
    }

    /** 使用 SI 前缀显示以基本单位计量的整数。 */
    public static String formatSi(long value) {
        return formatSi(value, false, null);
    }

    /** 格式化 SI 前缀。milli 表示传入值以基本单位的千分之一计量。 */
    public static String formatSi(long value, boolean milli, @Nullable String unit) {
        return formatSi(BigDecimal.valueOf(value), milli, decimalFormat("#,##0.##"), unit);
    }

    /** 显示带小数的数量。非有限值使用当前格式器的标准表示。 */
    public static String formatSi(double value, boolean milli, @Nullable String unit) {
        return formatSi(value, milli, decimalFormat("#,##0.##"), unit);
    }

    public static String formatSi(double value, boolean milli, NumberFormat numberFormat, @Nullable String unit) {
        return Double.isFinite(value) ? formatSi(BigDecimal.valueOf(value), milli, numberFormat, unit) : numberFormat.format(value) + (unit == null ? "" : unit);
    }

    /** 由调用方决定数字部分的舍入及区域设置。 */
    public static String formatSi(BigDecimal value, boolean milli, NumberFormat numberFormat, @Nullable String unit) {
        String suffix = unit == null ? "" : unit;
        if (value.signum() == 0) return numberFormat.format(value) + suffix;

        final String prefixes = "kMGTPEZ";
        boolean useMilli = milli && value.abs().compareTo(BigDecimal.valueOf(1_000)) < 0;
        BigDecimal scaled = milli && !useMilli ? value.movePointLeft(3) : value;
        int exponent = Math.clamp((scaled.precision() - (long) scaled.scale() - 1) / 3, 0, prefixes.length());
        String prefix = exponent > 0 ? String.valueOf(prefixes.charAt(exponent - 1)) : useMilli ? "m" : "";
        return numberFormat.format(scaled.movePointLeft(exponent * 3)) + prefix + suffix;
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

    /** 接受带单位后缀和算式的正整数，输入无效时返回空结果。 */
    public static OptionalLong parse(String input) {
        return parseLong(input, 1, Long.MAX_VALUE);
    }

    /** 接受任意精度的正整数数量。 */
    public static Optional<BigInteger> parseUnboundedPositiveInteger(String input) {
        var result = evaluate(input, MathContext.UNLIMITED);
        if (result.isEmpty()) return Optional.empty();
        BigDecimal value = result.get().stripTrailingZeros();
        if (value.signum() <= 0 || value.scale() > 0) return Optional.empty();
        try {
            return Optional.of(value.toBigIntegerExact());
        } catch (ArithmeticException invalidInteger) {
            return Optional.empty();
        }
    }

    /**
     * 库存设置允许用零清除。带小数的单位数量须能换算成整数个基础单位。
     * 单位大小必须为正数。输入无效时返回空结果。
     */
    public static OptionalLong parseAmount(String input, long amountPerUnit) {
        if (amountPerUnit <= 0) throw new IllegalArgumentException("Amount per unit must be positive");
        var result = evaluate(input, MathContext.UNLIMITED);
        return result.map(bigDecimal -> boundedLong(bigDecimal.multiply(BigDecimal.valueOf(amountPerUnit)), 0, Long.MAX_VALUE)).orElseGet(OptionalLong::empty);
    }

    /** 计算带符号的整数表达式，范围含两个端点。边界顺序颠倒时抛出异常。 */
    public static OptionalLong parseLong(String input, long minimum, long maximum) {
        if (minimum > maximum) throw new IllegalArgumentException("Minimum amount exceeds maximum");
        var result = evaluate(input, MathContext.UNLIMITED);
        return result.map(bigDecimal -> boundedLong(bigDecimal, minimum, maximum)).orElseGet(OptionalLong::empty);
    }

    /** 计算位于调用方指定 int 闭区间内的带符号整数。 */
    public static OptionalInt parseInt(String input, int minimum, int maximum) {
        var value = parseLong(input, minimum, maximum);
        return value.isPresent() ? OptionalInt.of((int) value.getAsLong()) : OptionalInt.empty();
    }

    /** 计算小数表达式，结果须为有限 double。 */
    public static OptionalDouble parseDouble(String input) {
        var result = evaluate(input, MathContext.DECIMAL128);
        if (result.isEmpty()) return OptionalDouble.empty();
        double value = result.get().doubleValue();
        return Double.isFinite(value) ? OptionalDouble.of(value) : OptionalDouble.empty();
    }

    /** 计算小数表达式，结果须位于有限 float 范围内。 */
    public static Optional<Float> parseFloat(String input) {
        var result = evaluate(input, MathContext.DECIMAL128);
        if (result.isEmpty()) return Optional.empty();
        float value = result.get().floatValue();
        return Float.isFinite(value) ? Optional.of(value) : Optional.empty();
    }

    private static Optional<BigDecimal> evaluate(String input, MathContext divisionContext) {
        try {
            return Optional.of(new Parser(input, divisionContext).parse());
        } catch (IllegalArgumentException | ArithmeticException invalidExpression) {
            return Optional.empty();
        }
    }

    private static OptionalLong boundedLong(BigDecimal value, long minimum, long maximum) {
        if (value.compareTo(BigDecimal.valueOf(minimum)) < 0 || value.compareTo(BigDecimal.valueOf(maximum)) > 0 ||
                value.stripTrailingZeros().scale() > 0)
            return OptionalLong.empty();
        return OptionalLong.of(value.longValueExact());
    }

    private static final class Parser {

        private final String s;
        private final MathContext divisionContext;
        private int p;

        Parser(String input, MathContext divisionContext) {
            this.divisionContext = divisionContext;
            var normalized = input.trim();
            if (normalized.startsWith("=")) {
                normalized = normalized.substring(1);
            }
            s = normalized.replace(" ", "").toLowerCase(Locale.ROOT);
        }

        BigDecimal parse() {
            BigDecimal v = expression();
            if (p != s.length()) throw new IllegalArgumentException();
            return v;
        }

        BigDecimal expression() {
            BigDecimal v = term();
            while (p < s.length()) {
                char c = s.charAt(p);
                if (c == '+') {
                    p++;
                    v = v.add(term());
                } else if (c == '-') {
                    p++;
                    v = v.subtract(term());
                } else break;
            }
            return v;
        }

        BigDecimal term() {
            BigDecimal v = unary();
            while (p < s.length()) {
                char c = s.charAt(p);
                if (c == '*') {
                    p++;
                    v = v.multiply(unary());
                } else if (c == '/') {
                    p++;
                    BigDecimal d = unary();
                    if (d.signum() == 0) throw new ArithmeticException();
                    v = v.divide(d, divisionContext);
                } else if (c == '(' || Character.isDigit(c) || c == '.') {
                    v = v.multiply(unary());
                } else break;
            }
            return v;
        }

        BigDecimal unary() {
            if (p < s.length() && (s.charAt(p) == '+' || s.charAt(p) == '-')) {
                boolean negative = s.charAt(p++) == '-';
                BigDecimal value = unary();
                return negative ? value.negate() : value;
            }
            return power();
        }

        BigDecimal power() {
            BigDecimal v = primary();
            if (p < s.length() && s.charAt(p) == '^') {
                p++;
                int n = unary().intValueExact();
                v = v.pow(n);
            }
            return v;
        }

        BigDecimal primary() {
            if (p < s.length() && s.charAt(p) == '(') {
                p++;
                BigDecimal v = expression();
                if (p >= s.length() || s.charAt(p++) != ')') throw new IllegalArgumentException();
                return v;
            }
            int st = p;
            while (p < s.length() && (Character.isDigit(s.charAt(p)) || s.charAt(p) == '.')) p++;
            if (st == p) throw new IllegalArgumentException();
            if (p < s.length() && s.charAt(p) == 'e' && p + 1 < s.length() && (Character.isDigit(s.charAt(p + 1)) || s.charAt(p + 1) == '+' || s.charAt(p + 1) == '-')) {
                p++;
                if (p < s.length() && (s.charAt(p) == '+' || s.charAt(p) == '-')) p++;
                int es = p;
                while (p < s.length() && Character.isDigit(s.charAt(p))) p++;
                if (es == p) throw new IllegalArgumentException();
            }
            BigDecimal v = new BigDecimal(s.substring(st, p));
            if (p < s.length()) {
                char u = s.charAt(p);
                long m = switch (u) {
                    case 'k' -> 1_000L;
                    case 'm' -> 1_000_000L;
                    case 'g' -> 1_000_000_000L;
                    case 't' -> 1_000_000_000_000L;
                    case 'p' -> 1_000_000_000_000_000L;
                    case 'e' -> 1_000_000_000_000_000_000L;
                    default -> 0L;
                };
                if (m != 0) {
                    p++;
                    v = v.multiply(BigDecimal.valueOf(m));
                }
            }
            return v;
        }
    }
}
