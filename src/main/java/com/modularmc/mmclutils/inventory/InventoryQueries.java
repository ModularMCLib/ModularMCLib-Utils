package com.modularmc.mmclutils.inventory;

import com.modularmc.mmclutils.fluid.FluidStackStrategies;
import com.modularmc.mmclutils.item.ItemStackStrategies;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

/** 按资源及其组件汇总库存和栈集合。 */
public final class InventoryQueries {

    private InventoryQueries() {}

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

    public static Object2IntMap<ItemStack> fromItemStackCollection(Iterable<ItemStack> inputs) {
        return fromItemStackCollection(inputs, false);
    }

    public static Object2IntMap<ItemStack> fromItemStackCollection(Iterable<ItemStack> inputs, boolean linked) {
        var counts = createItemStackMap(linked);
        for (ItemStack stack : inputs) {
            if (!stack.isEmpty()) counts.mergeInt(stack.copyWithCount(1), stack.getCount(), Math::addExact);
        }
        return counts;
    }

    public static Object2IntMap<ItemStack> createItemStackMap(boolean linked) {
        ItemStackStrategies strategy = ItemStackStrategies.comparingAllButCount();
        return linked ? new Object2IntLinkedOpenCustomHashMap<>(strategy) : new Object2IntOpenCustomHashMap<>(strategy);
    }

    public static Object2IntMap<FluidStack> fromFluidCollection(Iterable<FluidStack> inputs) {
        return fromFluidCollection(inputs, true);
    }

    public static Object2IntMap<FluidStack> fromFluidCollection(Iterable<FluidStack> inputs, boolean linked) {
        var counts = createFluidStackMap(linked);
        for (FluidStack stack : inputs) {
            if (!stack.isEmpty()) counts.mergeInt(stack.copyWithAmount(1), stack.getAmount(), Math::addExact);
        }
        return counts;
    }

    public static Object2IntMap<FluidStack> createFluidStackMap(boolean linked) {
        FluidStackStrategies strategy = FluidStackStrategies.comparingAllButAmount();
        return linked ? new Object2IntLinkedOpenCustomHashMap<>(strategy) : new Object2IntOpenCustomHashMap<>(strategy);
    }
}
