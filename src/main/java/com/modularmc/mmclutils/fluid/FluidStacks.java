package com.modularmc.mmclutils.fluid;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

/** 根据注册表查询流体标识，或将总量拆成可由单个流体栈表示的数量。 */
public final class FluidStacks {

    private FluidStacks() {}

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
}
