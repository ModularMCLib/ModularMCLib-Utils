package com.modularmc.mmclutils.utils;

import com.modularmc.mmclutils.fluid.FluidStackStrategies;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

/** 查询流体标识，并按流体和数据组件拆分或汇总流体栈。 */
public final class FluidUtils {

    private FluidUtils() {}

    public static Identifier id(FluidStack stack) {
        return BuiltInRegistries.FLUID.getKey(stack.getFluid());
    }

    public static List<FluidStack> split(FluidStack stack, long amount) {
        var result = new ObjectArrayList<FluidStack>();
        if (stack.isEmpty()) return result;
        while (amount > 0) {
            int size = (int) Math.min(amount, Integer.MAX_VALUE);
            result.add(stack.copyWithAmount(size));
            amount -= size;
        }
        return result;
    }

    public static Object2IntMap<FluidStack> countStacks(Iterable<FluidStack> inputs) {
        return countStacks(inputs, true);
    }

    public static Object2IntMap<FluidStack> countStacks(Iterable<FluidStack> inputs, boolean linked) {
        var counts = createStackCountMap(linked);
        for (FluidStack stack : inputs) {
            if (!stack.isEmpty()) counts.mergeInt(stack.copyWithAmount(1), stack.getAmount(), Math::addExact);
        }
        return counts;
    }

    public static Object2IntMap<FluidStack> createStackCountMap(boolean linked) {
        FluidStackStrategies strategy = FluidStackStrategies.comparingAllButAmount();
        return linked ? new Object2IntLinkedOpenCustomHashMap<>(strategy) : new Object2IntOpenCustomHashMap<>(strategy);
    }
}
