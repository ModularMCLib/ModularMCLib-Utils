package com.modularmc.mmclutils.serialization;

import com.google.common.reflect.TypeToken;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

/** 在序列化注册时查询类型信息。 */
public final class TypeTokens {

    private TypeTokens() {}

    public static Class<?> raw(Type type) {
        return TypeToken.of(type).getRawType();
    }

    public static Optional<Type> parameter(Type type, int index) {
        if (!(type instanceof ParameterizedType parameterized)) return Optional.empty();
        Type[] parameters = parameterized.getActualTypeArguments();
        return index < 0 || index >= parameters.length ? Optional.empty() : Optional.of(parameters[index]);
    }

    public static Optional<Type> component(Type type) {
        if (type instanceof GenericArrayType array) return Optional.of(array.getGenericComponentType());
        if (type instanceof Class<?> clazz && clazz.isArray()) return Optional.of(clazz.getComponentType());
        return Optional.empty();
    }
}
