package com.modularmc.mmclutils.api.collection;

import java.util.Iterator;

/** 依次遍历非空元素集合，到达末尾后从头继续。 */
public final class CyclicIterator<T> implements Iterable<T>, Iterator<T> {

    public static final CyclicIterator EMPTY = new CyclicIterator(new Object[0]);

    public static <T> CyclicIterator<T> empty() {
        return EMPTY;
    }

    public final T[] array;
    public final int size;
    private int index;
    private int remaining;

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
