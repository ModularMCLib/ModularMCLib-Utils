package com.modularmc.mmclutils.math;

import org.jspecify.annotations.NullMarked;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** 解析通用数值输入。整数数量保持精确，小数除法采用 DECIMAL128。 */
@NullMarked
public final class NumberExpressions {

    private NumberExpressions() {}

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
    public static OptionalLong parseStockAmount(String input, long amountPerUnit) {
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
