package com.modularmc.mmclutils.collection;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

import static org.apache.commons.lang3.ArrayUtils.*;

@SuppressWarnings("ForLoopReplaceableByForEach")
public class ArrayOperations {

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

    public static boolean allMatch(boolean[] array) {
        if (isEmpty(array) || array.length == 1) {
            return true;
        }

        boolean firstElement = array[0];
        if (array.length == 2) return firstElement == array[1];

        for (int i = 0; i < array.length; ++i) {
            if (array[i] != firstElement) {
                return false;
            }
        }
        return true;
    }

    public static boolean allMatch(byte[] array) {
        if (isEmpty(array) || array.length == 1) {
            return true;
        }

        byte firstElement = array[0];
        if (array.length == 2) return firstElement == array[1];

        for (int i = 0; i < array.length; ++i) {
            if (array[i] != firstElement) {
                return false;
            }
        }
        return true;
    }

    public static boolean allMatch(char[] array) {
        if (isEmpty(array) || array.length == 1) {
            return true;
        }

        char firstElement = array[0];
        if (array.length == 2) return firstElement == array[1];

        for (int i = 0; i < array.length; ++i) {
            if (array[i] != firstElement) {
                return false;
            }
        }
        return true;
    }

    public static boolean allMatch(double[] array) {
        if (isEmpty(array) || array.length == 1) {
            return true;
        }

        double firstElement = array[0];
        boolean searchNaN = Double.isNaN(firstElement);

        if (array.length == 2) return array[1] == firstElement || searchNaN && Double.isNaN(array[1]);

        for (int i = 0; i < array.length; ++i) {
            if (array[i] != firstElement && !(searchNaN && Double.isNaN(array[i]))) {
                return false;
            }
        }

        return false;
    }

    public static boolean allMatch(double[] array, double tolerance) {
        if (isEmpty(array) || array.length == 1) {
            return true;
        }

        double firstElement = array[0];
        double min = firstElement - tolerance;
        double max = firstElement + tolerance;

        if (array.length == 2) return array[1] >= min && array[1] <= max;

        for (int i = 0; i < array.length; ++i) {
            if (array[i] < min || array[i] > max) {
                return false;
            }
        }
        return true;
    }

    public static boolean allMatch(float[] array) {
        if (isEmpty(array) || array.length == 1) {
            return true;
        }

        float firstElement = array[0];
        boolean searchNaN = Float.isNaN(firstElement);

        if (array.length == 2) return array[1] == firstElement || searchNaN && Float.isNaN(array[1]);

        for (int i = 0; i < array.length; ++i) {
            if (array[i] != firstElement && !(searchNaN && Float.isNaN(array[i]))) {
                return false;
            }
        }

        return false;
    }

    public static boolean allMatch(float[] array, float tolerance) {
        if (isEmpty(array) || array.length == 1) {
            return true;
        }

        float firstElement = array[0];
        float min = firstElement - tolerance;
        float max = firstElement + tolerance;

        if (array.length == 2) return array[1] >= min && array[1] <= max;

        for (int i = 0; i < array.length; ++i) {
            if (array[i] < min && array[i] > max) {
                return false;
            }
        }
        return true;
    }

    public static boolean allMatch(int[] array) {
        if (isEmpty(array) || array.length == 1) {
            return true;
        }

        int firstElement = array[0];
        if (array.length == 2) return firstElement == array[1];

        for (int i = 0; i < array.length; ++i) {
            if (array[i] != firstElement) {
                return false;
            }
        }
        return true;
    }

    public static boolean allMatch(long[] array) {
        if (isEmpty(array) || array.length == 1) {
            return true;
        }

        long firstElement = array[0];
        if (array.length == 2) return firstElement == array[1];

        for (int i = 0; i < array.length; ++i) {
            if (array[i] != firstElement) {
                return false;
            }
        }
        return true;
    }

    public static boolean allMatch(Object[] array) {
        if (isEmpty(array) || array.length == 1) {
            return true;
        }

        Object firstElement = array[0];
        if (array.length == 2) return Objects.equals(array[1], firstElement);

        for (int i = 0; i < array.length; ++i) {
            if (!Objects.equals(array[i], firstElement)) {
                return false;
            }
        }
        return true;
    }

    public static boolean allMatch(short[] array) {
        if (isEmpty(array) || array.length == 1) {
            return true;
        }

        short firstElement = array[0];
        if (array.length == 2) return firstElement == array[1];

        for (int i = 0; i < array.length; ++i) {
            if (array[i] != firstElement) {
                return false;
            }
        }
        return true;
    }
}
