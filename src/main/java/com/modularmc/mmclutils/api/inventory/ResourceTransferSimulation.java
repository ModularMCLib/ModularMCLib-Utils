package com.modularmc.mmclutils.api.inventory;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * 保存物品槽位，在库存的所属线程执行连续插入和提取。
 * 阶段性值会被原库存读到并触发库存的变更通知。关闭前未提交时，按槽位恢复原值。
 */
public final class ResourceTransferSimulation implements AutoCloseable {

    private final IItemHandlerModifiable storage;
    private final ItemStack[] original;
    private boolean closed;

    /** 在槽位布局固定的库存上开始一轮仿真。 */
    public ResourceTransferSimulation(IItemHandlerModifiable storage) {
        this.storage = storage;
        original = new ItemStack[storage.getSlots()];
        for (int slot = 0; slot < original.length; slot++) {
            original[slot] = storage.getStackInSlot(slot).copy();
        }
    }

    /** 返回存储的槽位数。 */
    public int size() {
        return original.length;
    }

    /** 取得槽位物品的副本，包含本轮仿真的改动。 */
    public ItemStack resource(int index) {
        return storage.getStackInSlot(index).copy();
    }

    /** 返回指定槽位的当前数量，包含本轮仿真的改动。 */
    public long amount(int index) {
        return storage.getStackInSlot(index).getCount();
    }

    /** 查询指定物品在此槽位的容量。空栈用于查询槽位通用容量。 */
    public long capacity(int index, ItemStack resource) {
        int limit = storage.getSlotLimit(index);
        return resource.isEmpty() ? limit : Math.min(limit, resource.getMaxStackSize());
    }

    /** 优先插入已有同类物品的槽位，返回成功量。 */
    public int insert(ItemStack resource, int amount) {
        checkActive(resource, amount);
        return amount - ItemHandlerHelper.insertItemStacked(storage, resource.copyWithCount(amount), false).getCount();
    }

    /** 尝试向指定槽位插入非空物品，并返回接受的数量。 */
    public int insert(int index, ItemStack resource, int amount) {
        checkActive(resource, amount);
        return amount - storage.insertItem(index, resource.copyWithCount(amount), false).getCount();
    }

    /** 按槽位顺序提取指定物品，并返回提取的数量。 */
    public int extract(ItemStack resource, int amount) {
        checkActive(resource, amount);
        int remaining = amount;
        for (int slot = 0; slot < size() && remaining > 0; slot++) {
            remaining -= extract(slot, resource, remaining);
        }
        return amount - remaining;
    }

    /** 从指定槽位提取物品，并返回提取的数量。 */
    public int extract(int index, ItemStack resource, int amount) {
        checkActive(resource, amount);
        int remaining = amount;
        while (remaining > 0 && ItemStack.isSameItemSameComponents(storage.getStackInSlot(index), resource)) {
            int removed = storage.extractItem(index, remaining, false).getCount();
            if (removed == 0) break;
            remaining -= removed;
        }
        return amount - remaining;
    }

    /** 保留本轮对槽位的改动并结束仿真。 */
    public void commit() {
        ensureOpen();
        closed = true;
    }

    /** 恢复槽位，开启新一轮。 */
    public void reset() {
        ensureOpen();
        restore();
    }

    private void checkActive(ItemStack resource, int amount) {
        ensureOpen();
        if (resource.isEmpty() || amount < 0) throw new IllegalArgumentException("物品应为非空且转移数量应为非负数");
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("转移仿真已关闭");
    }

    private void restore() {
        for (int slot = 0; slot < original.length; slot++) {
            if (!ItemStack.matches(original[slot], storage.getStackInSlot(slot))) {
                storage.setStackInSlot(slot, original[slot].copy());
            }
        }
    }

    /** 结束仿真，未提交时恢复原库存。 */
    @Override
    public void close() {
        if (!closed) {
            restore();
            closed = true;
        }
    }
}
