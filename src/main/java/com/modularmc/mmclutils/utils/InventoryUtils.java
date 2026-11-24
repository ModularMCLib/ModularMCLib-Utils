package com.modularmc.mmclutils.utils;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.TransferPreconditions;
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

/**
 * 汇总、排序及转移 NeoForge 库存资源。调用方须在库存的所属线程访问。
 * 跨槽筛选转移和优先填充同类槽位可直接使用 {@link ResourceHandlerUtil}。
 */
public final class InventoryUtils {

    private InventoryUtils() {}

    /**
     * 从一个槽位向目标尝试转移，按双方都能接受的数量提交改动。
     *
     * @param source     提供资源的库存
     * @param sourceSlot 提取的槽位
     * @param target     接收资源的库存
     * @param maximum    允许转移的最大数量
     * @param parent     外层事务，空值表示本次调用自行提交
     * @param <R>        资源类型
     * @return 实际转移的数量
     * @throws IllegalArgumentException 最大数量为负数
     */
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

    /**
     * 从多个槽位提取指定资源，并完整转移相应数量。任一侧未达到指定数量时回滚。
     *
     * @param source   提供资源的库存
     * @param target   接收资源的库存
     * @param resource 要转移的非空资源，包含其数据组件
     * @param amount   必须完整转移的非负数量
     * @param parent   外层事务，空值表示本次调用自行提交
     * @param <R>      资源类型
     * @return 是否转移了指定的完整数量。零数量直接成功
     * @throws IllegalArgumentException 资源为空或数量为负数
     */
    public static <R extends Resource> boolean moveExact(ResourceHandler<R> source, ResourceHandler<R> target,
                                                         R resource, int amount, @Nullable TransactionContext parent) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (amount == 0) return true;
        if (source == target) return false;
        try (Transaction transaction = Transaction.open(parent)) {
            if (source.extract(resource, amount, transaction) != amount || target.insert(resource, amount, transaction) != amount) return false;
            transaction.commit();
            return true;
        }
    }

    /**
     * 按槽位顺序尝试插入资源，只使用尚未占用的槽位。目标库存的插入限制照常生效。
     *
     * @param target   接收资源的库存
     * @param resource 要插入的非空资源
     * @param amount   尝试插入的非负数量
     * @param parent   外层事务，空值表示本次调用自行提交
     * @param <R>      资源类型
     * @return 实际插入的数量
     * @throws IllegalArgumentException 资源为空或数量为负数
     */
    public static <R extends Resource> int insertIntoEmpty(ResourceHandler<R> target, R resource, int amount,
                                                           @Nullable TransactionContext parent) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (amount == 0) return 0;
        int inserted = 0;
        try (Transaction transaction = Transaction.open(parent)) {
            for (int slot = 0, slots = target.size(); slot < slots && inserted < amount; slot++) {
                if (target.getResource(slot).isEmpty()) inserted += target.insert(slot, resource, amount - inserted, transaction);
            }
            transaction.commit();
        }
        return inserted;
    }

    /** 按槽位顺序统计资源总量，保留各类资源首次出现的顺序。 */
    public static <R extends Resource> Object2LongMap<R> count(ResourceHandler<R> storage) {
        return count(storage, true);
    }

    /** 汇总同类资源的数量，可保留资源首次出现的顺序。总量超出 long 范围时抛出算术异常。 */
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

    /**
     * 尝试按给定顺序重新填充所有物品槽位。库存拒绝提取或重新插入时回滚整次排序。
     *
     * @param inventory 待整理的物品库存
     * @param order     含数据组件的物品资源排序规则
     * @param parent    外层事务，空值表示本次调用自行提交
     * @return 是否完成整理
     */
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
