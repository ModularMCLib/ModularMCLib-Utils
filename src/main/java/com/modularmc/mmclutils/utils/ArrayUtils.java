package com.modularmc.mmclutils.utils;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

import static org.apache.commons.lang3.ArrayUtils.isEmpty;

/** 数组复制、等值检查和有序查找。 */
public class ArrayUtils {

    /**
     * 复制数组，并递归复制多维数组的各级子数组。
     * 输入为空时返回空值。
     *
     * <p>
     * 非数组的引用元素沿用原引用。
     *
     * @param array 待复制数组
     * @param <T>   数组类型
     * @return 复制后的数组，输入为空时返回空值
     */
    @Contract(value = "!null -> !null; _ -> null", pure = true)
    public static <T> T @Nullable [] deepCopy(T @Nullable [] array) {
        if (array == null) {
            return null;
        }

        T[] copy = array.clone();
        if (array.getClass().getComponentType().isArray()) {
            for (int i = 0; i < array.length; ++i) {
                copy[i] = copySubarray(array[i]);
            }
        }

        return copy;
    }

    private static <T> @Nullable T copySubarray(@Nullable T value) {
        // clone 保留每个子数组的实际类型，与传入引用的类型一致。
        @SuppressWarnings("unchecked")
        T copy = (T) switch (value) {
            case null -> null;
            case Object[] array -> deepCopy(array);
            case boolean[] array -> array.clone();
            case byte[] array -> array.clone();
            case char[] array -> array.clone();
            case short[] array -> array.clone();
            case int[] array -> array.clone();
            case long[] array -> array.clone();
            case float[] array -> array.clone();
            case double[] array -> array.clone();
            default -> throw new IllegalArgumentException("需要数组类型的元素");
        };
        return copy;
    }

    /** 检查全部元素是否相等。空值、空数组和单元素数组均视为匹配。 */
    public static boolean allMatch(boolean @Nullable [] array) {
        if (isEmpty(array)) return true;
        boolean firstElement = array[0];
        for (int i = 1; i < array.length; ++i) {
            if (array[i] != firstElement) return false;
        }
        return true;
    }

    /** 检查全部元素是否相等。空值、空数组和单元素数组均视为匹配。 */
    public static boolean allMatch(byte @Nullable [] array) {
        if (isEmpty(array)) return true;
        byte firstElement = array[0];
        for (int i = 1; i < array.length; ++i) {
            if (array[i] != firstElement) return false;
        }
        return true;
    }

    /** 检查全部元素是否相等。空值、空数组和单元素数组均视为匹配。 */
    public static boolean allMatch(char @Nullable [] array) {
        if (isEmpty(array)) return true;
        char firstElement = array[0];
        for (int i = 1; i < array.length; ++i) {
            if (array[i] != firstElement) return false;
        }
        return true;
    }

    /**
     * 比较浮点元素。正负零视为相等，NaN 只与 NaN 匹配。
     *
     * @param array 待比较数组。空值、空数组和单元素数组均视为匹配
     * @return 全部元素是否与首元素匹配
     */
    public static boolean allMatch(double @Nullable [] array) {
        if (isEmpty(array)) return true;
        double firstElement = array[0];
        boolean firstIsNaN = Double.isNaN(firstElement);
        for (int i = 1; i < array.length; ++i) {
            if (array[i] != firstElement && !(firstIsNaN && Double.isNaN(array[i]))) return false;
        }
        return true;
    }

    /**
     * 以首元素为基准比较绝对误差。同号无穷视为相等。多元素数组包含 NaN 时返回 {@code false}。
     *
     * @param array     待比较数组。空值、空数组和单元素数组均视为匹配
     * @param tolerance 最大允许误差，须为有限非负数
     * @return 每个元素是否与首元素相同或误差在上限以内
     * @throws IllegalArgumentException 误差上限为负数或非有限值
     */
    public static boolean allMatch(double @Nullable [] array, double tolerance) {
        if (!(tolerance >= 0) || !Double.isFinite(tolerance)) throw new IllegalArgumentException("容差须为有限非负数");
        if (isEmpty(array)) return true;
        double firstElement = array[0];
        for (int i = 1; i < array.length; ++i) {
            double element = array[i];
            if (element != firstElement && !(Math.abs(element - firstElement) <= tolerance)) return false;
        }
        return true;
    }

    /**
     * 比较浮点元素。正负零视为相等，NaN 只与 NaN 匹配。
     *
     * @param array 待比较数组。空值、空数组和单元素数组均视为匹配
     * @return 全部元素是否与首元素匹配
     */
    public static boolean allMatch(float @Nullable [] array) {
        if (isEmpty(array)) return true;
        float firstElement = array[0];
        boolean firstIsNaN = Float.isNaN(firstElement);
        for (int i = 1; i < array.length; ++i) {
            if (array[i] != firstElement && !(firstIsNaN && Float.isNaN(array[i]))) return false;
        }
        return true;
    }

    /**
     * 以首元素为基准比较绝对误差。同号无穷视为相等。多元素数组包含 NaN 时返回 {@code false}。
     *
     * @param array     待比较数组。空值、空数组和单元素数组均视为匹配
     * @param tolerance 最大允许误差，须为有限非负数
     * @return 每个元素是否与首元素相同或误差在上限以内
     * @throws IllegalArgumentException 误差上限为负数或非有限值
     */
    public static boolean allMatch(float @Nullable [] array, float tolerance) {
        if (!(tolerance >= 0) || !Float.isFinite(tolerance)) throw new IllegalArgumentException("容差须为有限非负数");
        if (isEmpty(array)) return true;
        float firstElement = array[0];
        for (int i = 1; i < array.length; ++i) {
            float element = array[i];
            if (element != firstElement && !(Math.abs(element - firstElement) <= tolerance)) return false;
        }
        return true;
    }

    /** 检查全部元素是否相等。空值、空数组和单元素数组均视为匹配。 */
    public static boolean allMatch(int @Nullable [] array) {
        if (isEmpty(array)) return true;
        int firstElement = array[0];
        for (int i = 1; i < array.length; ++i) {
            if (array[i] != firstElement) return false;
        }
        return true;
    }

    /** 检查全部元素是否相等。空值、空数组和单元素数组均视为匹配。 */
    public static boolean allMatch(long @Nullable [] array) {
        if (isEmpty(array)) return true;
        long firstElement = array[0];
        for (int i = 1; i < array.length; ++i) {
            if (array[i] != firstElement) return false;
        }
        return true;
    }

    /** 使用 {@link Objects#equals} 比较元素。空值、空数组和单元素数组均视为匹配。 */
    public static boolean allMatch(Object @Nullable [] array) {
        if (isEmpty(array)) return true;
        Object firstElement = array[0];
        for (int i = 1; i < array.length; ++i) {
            if (!Objects.equals(array[i], firstElement)) return false;
        }
        return true;
    }

    /** 检查全部元素是否相等。空值、空数组和单元素数组均视为匹配。 */
    public static boolean allMatch(short @Nullable [] array) {
        if (isEmpty(array)) return true;
        short firstElement = array[0];
        for (int i = 1; i < array.length; ++i) {
            if (array[i] != firstElement) return false;
        }
        return true;
    }

    /**
     * 二分查找严格小于给定值的最后一个元素。
     *
     * @param array 按有符号长整数升序排列的数组，允许重复
     * @param value 查找上界，结果严格小于该值
     * @return 最后一个满足条件的下标，无匹配时返回 -1
     */
    public static int nearestLesser(long[] array, long value) {
        return value == Long.MIN_VALUE ? -1 : nearestLesserOrEqual(array, value - 1);
    }

    /**
     * 二分查找小于或等于给定值的最后一个元素。
     *
     * @param array 按有符号长整数升序排列的数组，允许重复
     * @param value 查找上界，结果可以等于该值
     * @return 最后一个满足条件的下标，无匹配时返回 -1
     */
    public static int nearestLesserOrEqual(long[] array, long value) {
        int low = 0, high = array.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (array[mid] <= value) low = mid + 1;
            else high = mid;
        }
        return low - 1;
    }

    /** 按元素顺序计算整型数组的哈希值。数组为空值时返回 0。 */
    public static int hashInts(int @Nullable... vals) {
        return Arrays.hashCode(vals);
    }

    /** 按元素顺序计算长整型数组的哈希值。数组为空值时返回 0。 */
    public static int hashLongs(long @Nullable... vals) {
        return Arrays.hashCode(vals);
    }
}
