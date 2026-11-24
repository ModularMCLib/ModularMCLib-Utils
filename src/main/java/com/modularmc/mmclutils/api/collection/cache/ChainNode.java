package com.modularmc.mmclutils.api.collection.cache;

/** 分段哈希表的链式节点。重排时只修改后继引用。 */
interface ChainNode<N extends ChainNode<N>> {

    N getNext();

    void setNext(N next);
}
