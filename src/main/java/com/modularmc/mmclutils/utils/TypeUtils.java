package com.modularmc.mmclutils.utils;

import com.google.common.reflect.TypeToken;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

/** 在序列化注册时查询类型信息。 */
public final class TypeUtils {

    private TypeUtils() {}

    /** 按 Guava 的类型解析规则返回原始运行时类。 */
    public static Class<?> raw(Type type) {
        return TypeToken.of(type).getRawType();
    }

    /** 查询直接声明的第 index 个类型实参。原始类型和越界索引返回空结果。 */
    public static Optional<Type> parameter(Type type, int index) {
        if (!(type instanceof ParameterizedType parameterized)) return Optional.empty();
        Type[] parameters = parameterized.getActualTypeArguments();
        return index < 0 || index >= parameters.length ? Optional.empty() : Optional.of(parameters[index]);
    }

    /** 读取普通数组或泛型数组的元素类型，其他类型返回空结果。 */
    public static Optional<Type> component(Type type) {
        if (type instanceof GenericArrayType array) return Optional.of(array.getGenericComponentType());
        if (type instanceof Class<?> clazz && clazz.isArray()) return Optional.of(clazz.getComponentType());
        return Optional.empty();
    }
}
