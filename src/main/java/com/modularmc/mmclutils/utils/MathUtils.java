package com.modularmc.mmclutils.utils;

import net.minecraft.util.Mth;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/** 数值范围、插值和数值转换。 */
public final class MathUtils {

    private MathUtils() {}

    public static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int lerpInt(double delta, int start, int end) {
        return start + Mth.floor(delta * (end - start));
    }

    public static int[] split(long value) {
        IntArrayList result = new IntArrayList();
        while (value > 0) {
            int intValue = (int) Math.min(value, Integer.MAX_VALUE);
            result.add(intValue);
            value -= intValue;
        }
        return result.toIntArray();
    }

    public static int saturatedCast(long value) {
        if (value > 2147483647L) {
            return Integer.MAX_VALUE;
        } else {
            return value < -2147483648L ? Integer.MIN_VALUE : (int) value;
        }
    }

    public static float ratio(BigInteger a, BigInteger b) {
        return new BigDecimal(a).divide(new BigDecimal(b), MathContext.DECIMAL32).floatValue();
    }

    public static int ceilDiv(int x, int y) {
        final int q = x / y;
        // 同号且存在余数时向上取整。
        if ((x ^ y) >= 0 && (q * y != x)) {
            return q + 1;
        }
        return q;
    }

    public static float min(float @NonNull... values) {
        // noinspection ConstantValue JSpecify 将数组声明为非空，保留范围检查。
        if (values == null || values.length == 0) throw new IllegalArgumentException();
        if (values.length == 1) return values[0];
        if (values.length == 2) return Math.min(values[0], values[1]);
        float min = Float.MAX_VALUE;
        for (float i : values) {
            if (i < min) {
                min = i;
            }
        }
        return min;
    }

    public static float max(float @NonNull... values) {
        // noinspection ConstantValue JSpecify 将数组声明为非空，保留范围检查。
        if (values == null || values.length == 0) throw new IllegalArgumentException();
        if (values.length == 1) return values[0];
        if (values.length == 2) return Math.max(values[0], values[1]);
        float max = -Float.MAX_VALUE;
        for (float i : values) {
            if (i > max) {
                max = i;
            }
        }
        return max;
    }
}
