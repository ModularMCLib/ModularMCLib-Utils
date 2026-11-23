package com.modularmc.mmclutils.collection.map.enums;

/** 缓存每种枚举类型的常量，供按序号寻址的容器共用。 */
public final class EnumMaps {

    private EnumMaps() {}

    private static final ClassValue<Object[]> KEY_UNIVERSES = new ClassValue<>() {

        @Override
        protected Object[] computeValue(Class<?> type) {
            return type.getEnumConstants();
        }
    };

    @SuppressWarnings("unchecked")
    public static <K extends Enum<K>> K[] universe(Class<K> keyType) {
        return (K[]) KEY_UNIVERSES.get(keyType);
    }
}
