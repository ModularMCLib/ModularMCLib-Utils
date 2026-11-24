package com.modularmc.mmclutils.utils;

import com.modularmc.mmclutils.api.inventory.ItemStackStrategy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

/** 查询标识、比较、拆分和计数物品栈，保留物品组件。 */
public final class ItemUtils {

    private ItemUtils() {}

    public static Identifier id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    /** 按该物品的正常堆叠上限拆分，保留数据组件。 */
    public static List<ItemStack> split(ItemStack stack, long amount) {
        return split(stack, amount, stack.getMaxStackSize());
    }

    /** 以指定上限拆分，适用于只受 int 数量限制的存储。每个结果栈可独立修改。 */
    public static List<ItemStack> split(ItemStack stack, long amount, int maximumPerStack) {
        if (maximumPerStack <= 0) throw new IllegalArgumentException("单个物品栈的数量上限须为正数");
        var result = new ObjectArrayList<ItemStack>();
        if (stack.isEmpty()) return result;
        while (amount > 0) {
            int size = (int) Math.min(amount, maximumPerStack);
            result.add(stack.copyWithCount(size));
            amount -= size;
        }
        return result;
    }

    public static boolean matches(ItemStack first, ItemStack second) {
        return first.getCount() == second.getCount() && ItemStack.isSameItemSameComponents(first, second);
    }

    public static Object2IntMap<ItemStack> countStacks(Iterable<ItemStack> inputs) {
        return countStacks(inputs, false);
    }

    public static Object2IntMap<ItemStack> countStacks(Iterable<ItemStack> inputs, boolean linked) {
        var counts = createStackCountMap(linked);
        for (ItemStack stack : inputs) {
            if (!stack.isEmpty()) counts.mergeInt(stack.copyWithCount(1), stack.getCount(), Math::addExact);
        }
        return counts;
    }

    public static Object2IntMap<ItemStack> createStackCountMap(boolean linked) {
        ItemStackStrategy strategy = ItemStackStrategy.comparingAllButCount();
        return linked ? new Object2IntLinkedOpenCustomHashMap<>(strategy) : new Object2IntOpenCustomHashMap<>(strategy);
    }
}
