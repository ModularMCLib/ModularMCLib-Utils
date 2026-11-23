package com.modularmc.mmclutils.inventory;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import org.jspecify.annotations.Nullable;

/**
 * 在所属线程中仿真连续插入和提取，复用存储自身的规则。
 * 在关闭或重置前，同一线程通过原始存储能看到仿真中的数量。使用 try-with-resources 限定作用范围。
 */
public final class ResourceTransferSimulation<R extends Resource> implements AutoCloseable {

    private final ResourceHandler<R> storage;
    private final @Nullable TransactionContext parent;
    private @Nullable Transaction transaction;

    public ResourceTransferSimulation(ResourceHandler<R> storage) {
        this(storage, null);
    }

    public ResourceTransferSimulation(ResourceHandler<R> storage, @Nullable TransactionContext parent) {
        this.storage = storage;
        this.parent = parent;
        transaction = Transaction.open(parent);
    }

    public int size() {
        return storage.size();
    }

    public R resource(int index) {
        return storage.getResource(index);
    }

    public long amount(int index) {
        return storage.getAmountAsLong(index);
    }

    /** 按存储的插入顺序尝试插入，返回成功量。 */
    public int insert(R resource, int amount) {
        return storage.insert(resource, amount, active());
    }

    public int insert(int index, R resource, int amount) {
        return storage.insert(index, resource, amount, active());
    }

    public int extract(R resource, int amount) {
        return storage.extract(resource, amount, active());
    }

    public int extract(int index, R resource, int amount) {
        return storage.extract(index, resource, amount, active());
    }

    /** 回滚仿真中的所有改动，开启新一轮。 */
    public void reset() {
        active().close();
        transaction = Transaction.open(parent);
    }

    private Transaction active() {
        if (transaction == null) throw new IllegalStateException("转移仿真已关闭");
        return transaction;
    }

    @Override
    public void close() {
        if (transaction != null) {
            transaction.close();
            transaction = null;
        }
    }
}
