package com.modularmc.mmclutils.inventory;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import org.jspecify.annotations.Nullable;

/** 在同一事务内转移资源，目标接收失败时恢复源槽位。 */
public final class InventoryTransfers {

    private InventoryTransfers() {}

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
}
