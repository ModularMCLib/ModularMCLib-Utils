package com.modularmc.mmclutils.collection.cache;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/** 调整开放寻址探测链。 */
final class WeakReferenceNode<T> extends WeakReference<T> implements ChainNode<WeakReferenceNode<T>> {

    final int hash;
    volatile WeakReferenceNode<T> next;

    WeakReferenceNode(T value, int hash, WeakReferenceNode<T> next, ReferenceQueue<Object> queue) {
        super(value, queue);
        this.hash = hash;
        this.next = next;
    }

    @Override
    public WeakReferenceNode<T> getNext() {
        return next;
    }

    @Override
    public void setNext(WeakReferenceNode<T> next) {
        this.next = next;
    }
}
