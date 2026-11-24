package com.modularmc.mmclutils.api.collection;

import java.util.Iterator;

/** 依次遍历非空元素集合，到达末尾后从头继续。 */
public final class CyclicIterator<T> implements Iterable<T>, Iterator<T> {

    /** 供空遍历共用的实例。泛型调用使用 {@link #empty()}。 */
    public static final CyclicIterator EMPTY = new CyclicIterator(new Object[0]);

    /** 取得可用作任意元素类型的空迭代器。 */
    public static <T> CyclicIterator<T> empty() {
        return EMPTY;
    }

    /** 保留的原始数组引用，迭代器直接读取此数组中的元素。 */
    public final T[] array;
    /** 一次遍历所包含的元素数。 */
    public final int size;
    private int index;
    private int remaining;

    /** 包装数组。每次调用 {@link #iterator()} 重置剩余次数，沿用上次停下的数组位置。 */
    public CyclicIterator(T[] array) {
        this.array = array;
        this.size = this.array.length;
    }

    @Override
    public Iterator<T> iterator() {
        remaining = size;
        return this;
    }

    @Override
    public boolean hasNext() {
        return remaining > 0;
    }

    @Override
    public T next() {
        if (index == size) index = 0;
        T element = array[index++];
        remaining--;
        return element;
    }
}
