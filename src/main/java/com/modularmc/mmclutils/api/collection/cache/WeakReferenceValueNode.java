package com.modularmc.mmclutils.api.collection.cache;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/** 更新链表链接并保持探测顺序。 */
final class WeakReferenceValueNode<K, V> extends WeakReference<V> implements ChainNode<WeakReferenceValueNode<K, V>> {

    final K key;
    final int hash;
    volatile WeakReferenceValueNode<K, V> next;

    WeakReferenceValueNode(K key, V value, int hash, WeakReferenceValueNode<K, V> next, ReferenceQueue<Object> queue) {
        super(value, queue);
        this.key = key;
        this.hash = hash;
        this.next = next;
    }

    @Override
    public WeakReferenceValueNode<K, V> getNext() {
        return next;
    }

    @Override
    public void setNext(WeakReferenceValueNode<K, V> next) {
        this.next = next;
    }
}
