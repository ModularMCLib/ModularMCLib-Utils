package com.modularmc.mmclutils.utils;

import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;

import java.util.Collection;

/** 将列表和集合复制为不可变容器，可复用本库已持有的不可变副本。 */
public final class CollectionUtils {

    private CollectionUtils() {}

    /** 以迭代顺序复制元素并返回不可变列表。已有不可变副本可以直接复用。 */
    public static <E> ObjectList<E> immutableList(Collection<? extends E> source) {
        if (source.isEmpty()) return ObjectList.of();
        if (source instanceof ObjectImmutableList<?>) {
            // 不可变列表可以按更宽的元素类型读取。
            @SuppressWarnings("unchecked")
            ObjectList<E> immutable = (ObjectList<E>) source;
            return immutable;
        }
        return new ObjectImmutableList<>(source);
    }

    /** 按相等性去重并返回不可变集合。元素引用沿用源集合，迭代顺序由散列表决定。 */
    public static <E> ObjectSet<E> immutableSet(Collection<? extends E> source) {
        if (source.isEmpty()) return ObjectSets.emptySet();
        if (source instanceof ImmutableSetCopy<?>) {
            // 后备集合为该副本独有，可直接复用。
            @SuppressWarnings("unchecked")
            ObjectSet<E> immutable = (ObjectSet<E>) source;
            return immutable;
        }
        return new ImmutableSetCopy<>(new ObjectOpenHashSet<>(source));
    }

    private static final class ImmutableSetCopy<E> extends ObjectSets.UnmodifiableSet<E> {

        private ImmutableSetCopy(ObjectSet<E> owned) {
            super(owned);
        }
    }
}
