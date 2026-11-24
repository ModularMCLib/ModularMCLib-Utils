package com.modularmc.mmclutils.api.inventory;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import org.jspecify.annotations.Nullable;

/**
 * 在所属线程中仿真连续插入和提取，复用存储自身的规则。
 * 在关闭或重置前，同一线程通过原始存储能看到仿真中的数量。使用 try-with-resources 限定作用范围。
 *
 * @param <R> 存储的资源类型
 */
public final class ResourceTransferSimulation<R extends Resource> implements AutoCloseable {

    private final ResourceHandler<R> storage;
    private final @Nullable TransactionContext parent;
    private @Nullable Transaction transaction;

    /** 在独立的事务中打开仿真，仿真结束时回滚。 */
    public ResourceTransferSimulation(ResourceHandler<R> storage) {
        this(storage, null);
    }

    /** 在指定外层事务的当前状态上开始仿真。外层为空时使用独立事务。 */
    public ResourceTransferSimulation(ResourceHandler<R> storage, @Nullable TransactionContext parent) {
        this.storage = storage;
        this.parent = parent;
        transaction = Transaction.open(parent);
    }

    /** 返回存储的槽位数。 */
    public int size() {
        return storage.size();
    }

    /** 查询指定槽位当前的资源，空槽由存储返回空资源。 */
    public R resource(int index) {
        return storage.getResource(index);
    }

    /** 返回指定槽位的当前数量，包含本轮仿真的改动。 */
    public long amount(int index) {
        return storage.getAmountAsLong(index);
    }

    /**
     * 查询指定资源在此槽位的容量。该值由存储提供，可用于估算，实际插入仍由事务验证。
     *
     * @param index    槽位索引
     * @param resource 待查询的资源，空资源查询槽位通用容量
     * @return 槽位容量
     */
    public long capacity(int index, R resource) {
        return storage.getCapacityAsLong(index, resource);
    }

    /** 按存储的插入顺序尝试插入，返回成功量。 */
    public int insert(R resource, int amount) {
        return storage.insert(resource, amount, active());
    }

    /** 尝试向指定槽位插入非空资源，并返回本轮实际接受的数量。 */
    public int insert(int index, R resource, int amount) {
        return storage.insert(index, resource, amount, active());
    }

    /** 按存储的提取顺序提取指定资源，并返回本轮实际提取的数量。 */
    public int extract(R resource, int amount) {
        return storage.extract(resource, amount, active());
    }

    /** 从指定槽位提取资源，并返回本轮实际提取的数量。 */
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

    /** 关闭事务，恢复仿真开始前的存储状态。 */
    @Override
    public void close() {
        if (transaction != null) {
            transaction.close();
            transaction = null;
        }
    }
}
