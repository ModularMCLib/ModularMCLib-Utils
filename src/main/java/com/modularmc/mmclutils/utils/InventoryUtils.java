package com.modularmc.mmclutils.utils;

import com.modularmc.mmclutils.api.inventory.ItemStackStrategy;
import com.modularmc.mmclutils.api.inventory.ResourceTransferSimulation;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.Comparator;

/** 汇总、排序及转移 NeoForge 物品库存。调用方须在库存的所属线程访问。 */
public final class InventoryUtils {

    private InventoryUtils() {}

    /**
     * 从一个槽位向目标尝试转移，优先填充目标中已有的同类堆叠。
     * 实际插入量与预览不一致时，恢复两个库存。
     */
    public static int move(IItemHandlerModifiable source, int sourceSlot, IItemHandlerModifiable target, int maximum) {
        if (maximum < 0) throw new IllegalArgumentException("转移数量应为非负数");
        if (source == target || maximum == 0) return 0;
        ItemStack available = source.extractItem(sourceSlot, maximum, true);
        if (available.isEmpty()) return 0;
        int accepted = available.getCount() - ItemHandlerHelper.insertItemStacked(target, available, true).getCount();
        if (accepted == 0) return 0;

        try (var sourceState = new ResourceTransferSimulation(source);
                var targetState = new ResourceTransferSimulation(target)) {
            ItemStack removed = source.extractItem(sourceSlot, accepted, false);
            if (removed.getCount() != accepted || !ItemStack.isSameItemSameComponents(available, removed)) return 0;
            if (!ItemHandlerHelper.insertItemStacked(target, removed, false).isEmpty()) return 0;
            sourceState.commit();
            targetState.commit();
            return accepted;
        }
    }

    /**
     * 从多个槽位提取指定物品及组件，并完整转移相应数量。任一侧未达到指定数量时恢复槽位。
     * 使用可写槽位以便恢复库存，调用期间库存的槽位结构须保持稳定。
     */
    public static boolean moveExact(IItemHandlerModifiable source, IItemHandlerModifiable target, ItemStack resource, int amount) {
        checkResource(resource, amount);
        if (amount == 0) return true;
        if (source == target) return false;
        ItemStack stack = resource.copyWithCount(amount);
        if (!ItemHandlerHelper.insertItemStacked(target, stack, true).isEmpty()) return false;
        try (var sourceState = new ResourceTransferSimulation(source);
                var targetState = new ResourceTransferSimulation(target)) {
            if (sourceState.extract(resource, amount) != amount) return false;
            if (!ItemHandlerHelper.insertItemStacked(target, stack, false).isEmpty()) return false;
            sourceState.commit();
            targetState.commit();
            return true;
        }
    }

    /** 按槽位顺序尝试插入，只使用尚未占用的槽位，目标库存的插入限制照常生效。 */
    public static int insertIntoEmpty(IItemHandler target, ItemStack resource, int amount) {
        checkResource(resource, amount);
        int remaining = amount;
        for (int slot = 0, slots = target.getSlots(); slot < slots && remaining > 0; slot++) {
            if (target.getStackInSlot(slot).isEmpty()) {
                remaining = target.insertItem(slot, resource.copyWithCount(remaining), false).getCount();
            }
        }
        return amount - remaining;
    }

    /** 按槽位顺序统计物品总量，保留各类物品首次出现的顺序。 */
    public static Object2LongMap<ItemStack> count(IItemHandler storage) {
        return count(storage, true);
    }

    /** 按物品与组件统计数量，键为数量一的副本，可保留资源首次出现的顺序。 */
    public static Object2LongMap<ItemStack> count(IItemHandler storage, boolean ordered) {
        var strategy = ItemStackStrategy.comparingAllButCount();
        Object2LongMap<ItemStack> counts = ordered ? new Object2LongLinkedOpenCustomHashMap<>(strategy) :
                new Object2LongOpenCustomHashMap<>(strategy);
        for (int slot = 0, slots = storage.getSlots(); slot < slots; slot++) {
            ItemStack stack = storage.getStackInSlot(slot);
            if (!stack.isEmpty()) counts.mergeLong(stack.copyWithCount(1), stack.getCount(), Math::addExact);
        }
        return counts;
    }

    /**
     * 尝试按给定顺序重新填充所有物品槽位。库存拒绝提取或重新插入时恢复槽位。
     * 比较器接收包含组件、数量为一的物品栈。
     */
    public static boolean sort(IItemHandlerModifiable inventory, Comparator<ItemStack> order) {
        var amounts = count(inventory);
        var resources = new ObjectArrayList<>(amounts.keySet());
        resources.sort(order);
        try (var state = new ResourceTransferSimulation(inventory)) {
            for (int slot = 0; slot < state.size(); slot++) {
                ItemStack stack = state.resource(slot);
                if (!stack.isEmpty() && state.extract(slot, stack, stack.getCount()) != stack.getCount()) return false;
            }
            for (ItemStack resource : resources) {
                long remaining = amounts.getLong(resource);
                for (int slot = 0; slot < state.size() && remaining > 0; slot++) {
                    remaining -= state.insert(slot, resource, (int) Math.min(Integer.MAX_VALUE, remaining));
                }
                if (remaining != 0) return false;
            }
            state.commit();
            return true;
        }
    }

    private static void checkResource(ItemStack resource, int amount) {
        if (resource.isEmpty() || amount < 0) throw new IllegalArgumentException("物品应为非空且转移数量应为非负数");
    }
}
