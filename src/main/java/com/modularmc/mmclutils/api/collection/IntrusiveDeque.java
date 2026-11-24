package com.modularmc.mmclutils.api.collection;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

/** 将链接字段保存在节点上，提供常数时间的队头和队尾操作。 */
public class IntrusiveDeque<T extends IntrusiveDeque.LinkNode<T>> implements Iterable<T> {

    /** 由队列链接操作维护的首节点，空队列时为空值。 */
    protected T first;
    /** 由队列链接操作维护的尾节点，空队列时为空值。 */
    protected T last;
    /** 当前链接在此队列上的节点数。 */
    protected int size;

    /** 判断队列是否没有链接节点。 */
    public boolean isEmpty() {
        return first == null;
    }

    /** 取得当前节点数，时间复杂度为常数。 */
    public int size() {
        return size;
    }

    /** 读取首节点，空队列时返回空值。 */
    public T peekFirst() {
        return first;
    }

    /** 读取尾节点，空队列时返回空值。 */
    public T peekLast() {
        return last;
    }

    /** 在队尾链接一个游离节点。调用方须保证该节点未加入任何队列。 */
    public void addLast(T node) {
        final T l = last;
        if (l == null) {
            first = node;
        } else {
            l.setNext(node);
            node.setPrev(l);
        }
        last = node;
        size++;
    }

    /** 在队首链接一个游离节点。调用方须保证该节点未加入任何队列。 */
    public void addFirst(T node) {
        final T f = first;
        if (f == null) {
            last = node;
        } else {
            f.setPrev(node);
            node.setNext(f);
        }
        first = node;
        size++;
    }

    /** 直接移除且清除已有节点的链接。传入的节点须已属于此队列。 */
    public void unlink(T node) {
        final T prev = node.getPrev();
        final T next = node.getNext();
        if (prev == null) {
            first = next;
        } else {
            prev.setNext(next);
            node.setPrev(null);
        }
        if (next == null) {
            last = prev;
        } else {
            next.setPrev(prev);
            node.setNext(null);
        }
        size--;
    }

    /** 若节点已在此队列中则移除。请勿传入属于其他队列的节点。 */
    public boolean remove(T node) {
        if (node.getPrev() != null || node.getNext() != null || node == first) {
            unlink(node);
            return true;
        }
        return false;
    }

    /** 取出首节点并清理其链接，空队列时返回空值。 */
    public T pollFirst() {
        final T f = first;
        if (f == null) return null;
        unlink(f);
        return f;
    }

    /** 取出尾节点并清理其链接，空队列时返回空值。 */
    public T pollLast() {
        final T l = last;
        if (l == null) return null;
        unlink(l);
        return l;
    }

    /** 取出首节点。空队列时抛出 {@link NoSuchElementException}。 */
    public T removeFirst() {
        final T f = pollFirst();
        if (f == null) throw new NoSuchElementException();
        return f;
    }

    /** 取出尾节点。空队列时抛出 {@link NoSuchElementException}。 */
    public T removeLast() {
        final T l = pollLast();
        if (l == null) throw new NoSuchElementException();
        return l;
    }

    /** 将另一个队列拼到尾端并清空对方队列。时间复杂度为常数。 */
    public void merge(IntrusiveDeque<T> other) {
        if (other.first == null || other == this) return;
        final T otherFirst = other.first;
        final T otherLast = other.last;
        if (last == null) {
            first = otherFirst;
        } else {
            last.setNext(otherFirst);
            otherFirst.setPrev(last);
        }
        last = otherLast;
        size += other.size;
        other.first = null;
        other.last = null;
        other.size = 0;
    }

    /** 断开队列中所有节点的前后链接。被清出的节点可以加入另一条队列。 */
    public void clear() {
        for (T node = first; node != null;) {
            T next = node.getNext();
            node.setPrev(null);
            node.setNext(null);
            node = next;
        }
        first = null;
        last = null;
        size = 0;
    }

    @Override
    public Iterator<T> iterator() {
        return new QueueIterator();
    }

    /** 按节点身份线性查找。可用于调用者没有节点所属队列信息的情况。 */
    public boolean contains(T node) {
        for (T n = first; n != null; n = n.getNext()) {
            if (n == node) return true;
        }
        return false;
    }

    /** 按队头到队尾的顺序复制节点引用。 */
    public Object[] toArray() {
        Object[] arr = new Object[size];
        int i = 0;
        for (T n = first; n != null; n = n.getNext()) {
            arr[i++] = n;
        }
        return arr;
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        for (T n = first; n != null; n = n.getNext()) {
            action.accept(n);
        }
    }

    @Override
    public Spliterator<T> spliterator() {
        return Spliterators.spliterator(iterator(), size, Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.NONNULL);
    }

    private final class QueueIterator implements Iterator<T> {

        private T nextNode = first;
        private T currentNode;

        @Override
        public boolean hasNext() {
            return nextNode != null;
        }

        @Override
        public T next() {
            var nextNode = this.nextNode;
            if (nextNode == null) throw new NoSuchElementException();
            this.currentNode = nextNode;
            this.nextNode = nextNode.getNext();
            return nextNode;
        }

        @Override
        public void remove() {
            var currentNode = this.currentNode;
            if (currentNode == null) throw new IllegalStateException();
            IntrusiveDeque.this.unlink(currentNode);
            this.currentNode = null;
        }

        @Override
        public void forEachRemaining(Consumer<? super T> action) {
            while (hasNext()) action.accept(next());
        }
    }

    /**
     * 为侵入式双端队列提供前后节点链接。
     * 实现对象同一时刻只能加入一条链，链接初始为空且由所属队列维护。
     *
     * @param <T> 实际节点类型
     */
    public interface LinkNode<T extends LinkNode<T>> {

        /** 返回当前节点的前驱，作为队首时返回空值。 */
        T getPrev();

        /** 更新前驱链接，由所属队列调用。 */
        void setPrev(T prev);

        /** 返回当前节点的后继，作为队尾时返回空值。 */
        T getNext();

        /** 更新后继链接，由所属队列调用。 */
        void setNext(T next);
    }

    /** 直接保存前后链接的节点基类。链接只能由所属队列维护。 */
    public abstract static class AbstractLinkNode<T extends AbstractLinkNode<T>> implements LinkNode<T> {

        /** 前驱节点，游离或在队首时为空值。 */
        protected T prev;

        /** 后继节点，游离或在队尾时为空值。 */
        protected T next;

        @Override
        public T getPrev() {
            return prev;
        }

        @Override
        public void setPrev(T prev) {
            this.prev = prev;
        }

        @Override
        public T getNext() {
            return next;
        }

        @Override
        public void setNext(T next) {
            this.next = next;
        }
    }
}
