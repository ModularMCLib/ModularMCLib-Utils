package com.modularmc.mmclutils.utils;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

/** 汇总、排序及转移 NeoForge 库存资源。修改库存时使用可回滚事务。 */
public final class InventoryUtils {

    private InventoryUtils() {}

    public static <R extends Resource> int move(ResourceHandler<R> source, int sourceSlot, ResourceHandler<R> target,
                                                int maximum, @Nullable TransactionContext parent) {
        if (maximum < 0) throw new IllegalArgumentException("转移数量应为非负数");
        if (source == target || maximum == 0) return 0;
        R resource = source.getResource(sourceSlot);
        if (resource.isEmpty()) return 0;
        try (Transaction transaction = Transaction.open(parent)) {
            int removed;
            int accepted;
            try (Transaction attempt = Transaction.open(transaction)) {
                removed = source.extract(sourceSlot, resource, maximum, attempt);
                if (removed == 0) return 0;
                accepted = target.insert(resource, removed, attempt);
                if (accepted == removed) {
                    attempt.commit();
                }
            }
            if (accepted == 0) return 0;
            if (accepted < removed) {
                if (source.extract(sourceSlot, resource, accepted, transaction) != accepted) return 0;
                if (target.insert(resource, accepted, transaction) != accepted) return 0;
            }
            transaction.commit();
            return accepted;
        }
    }

    /** 按槽位顺序统计资源总量，保留各类资源首次出现的顺序。 */
    public static <R extends Resource> Object2LongMap<R> count(ResourceHandler<R> storage) {
        return count(storage, true);
    }

    public static <R extends Resource> Object2LongMap<R> count(ResourceHandler<R> storage, boolean ordered) {
        Object2LongMap<R> counts = ordered ? new Object2LongLinkedOpenHashMap<>() : new Object2LongOpenHashMap<>();
        for (int i = 0, slots = storage.size(); i < slots; i++) {
            long amount = storage.getAmountAsLong(i);
            if (amount == 0) continue;
            R resource = storage.getResource(i);
            counts.put(resource, Math.addExact(counts.getLong(resource), amount));
        }
        return counts;
    }

    public static boolean sort(ResourceHandler<ItemResource> inventory, Comparator<ItemResource> order,
                               @Nullable TransactionContext parent) {
        var amounts = new Object2LongLinkedOpenHashMap<ItemResource>();
        try (Transaction transaction = Transaction.open(parent)) {
            for (int slot = 0; slot < inventory.size(); slot++) {
                ItemResource resource = inventory.getResource(slot);
                long remaining = inventory.getAmountAsLong(slot);
                if (resource.isEmpty() || remaining == 0) continue;
                amounts.addTo(resource, remaining);
                while (remaining > 0) {
                    int amount = (int) Math.min(Integer.MAX_VALUE, remaining);
                    if (inventory.extract(slot, resource, amount, transaction) != amount) return false;
                    remaining -= amount;
                }
            }
            var resources = new ObjectArrayList<>(amounts.keySet());
            resources.sort(order);
            for (ItemResource resource : resources) {
                long remaining = amounts.getLong(resource);
                for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
                    int inserted = inventory.insert(slot, resource, (int) Math.min(Integer.MAX_VALUE, remaining), transaction);
                    remaining -= inserted;
                }
                if (remaining != 0) return false;
            }
            transaction.commit();
            return true;
        }
    }
}
