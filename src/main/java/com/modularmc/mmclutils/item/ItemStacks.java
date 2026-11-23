package com.modularmc.mmclutils.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

/** 比较和拆分物品栈，保留物品组件。 */
public final class ItemStacks {

    private ItemStacks() {}

    public static Identifier id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    public static List<ItemStack> split(ItemStack stack, long amount) {
        var result = new ObjectArrayList<ItemStack>();
        if (stack.isEmpty()) return result;
        int maximum = stack.getMaxStackSize();
        while (amount > 0) {
            int size = (int) Math.min(amount, maximum);
            result.add(stack.copyWithCount(size));
            amount -= size;
        }
        return result;
    }

    public static boolean matches(ItemStack first, ItemStack second) {
        return first.getCount() == second.getCount() && ItemStack.isSameItemSameComponents(first, second);
    }
}
