package com.modularmc.mmclutils.inventory;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

/** 汇总同类物品并按调用方给定的顺序重排。任何槽位操作失败时回滚。 */
public final class InventorySorting {

    private InventorySorting() {}

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
