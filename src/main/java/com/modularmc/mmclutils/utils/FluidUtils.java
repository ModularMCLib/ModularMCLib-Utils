package com.modularmc.mmclutils.utils;

import com.modularmc.mmclutils.api.inventory.FluidStackStrategy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

/** 查询流体标识，并按流体和数据组件拆分或汇总流体栈。 */
public final class FluidUtils {

    private FluidUtils() {}

    /** 返回流体栈的注册表标识。 */
    public static Identifier id(FluidStack stack) {
        return id(stack.getFluid());
    }

    /** 返回已注册流体的标识。 */
    public static Identifier id(Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(fluid);
    }

    /** 显示以毫桶计量的容量，按大小使用 mB、B 或带 SI 前缀的桶数。 */
    public static String formatAmount(long amount) {
        return NumberUtils.formatSi(amount, true, "B");
    }

    /** 生成包含毫桶数量和流体标识的调试文本。 */
    public static String formatStack(FluidStack stack) {
        Identifier id = id(stack);
        return stack.getAmount() + "x_" + id.getNamespace() + "_" + id.getPath();
    }

    /** 判断流体栈能否均分成指定数量的非空份。 */
    public static boolean isAmountDivisible(FluidStack stack, int divisor) {
        if (divisor <= 0) throw new IllegalArgumentException("流体份数须为正数");
        int amount = stack.getAmount();
        return amount >= divisor && amount % divisor == 0;
    }

    /** 按单栈可表示的 int 数量上限拆分毫桶数。结果保留流体组件，元素可独立修改。 */
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

    /** 按流体与组件汇总毫桶数，不保留输入顺序。 */
    public static Object2IntMap<FluidStack> countStacks(Iterable<FluidStack> inputs) {
        return countStacks(inputs, true);
    }

    /** 按流体与组件汇总毫桶数。键是数量为一的副本，可选择保留首次出现顺序。 */
    public static Object2IntMap<FluidStack> countStacks(Iterable<FluidStack> inputs, boolean linked) {
        var counts = createStackCountMap(linked);
        for (FluidStack stack : inputs) {
            if (!stack.isEmpty()) counts.mergeInt(stack.copyWithAmount(1), stack.getAmount(), Math::addExact);
        }
        return counts;
    }

    /** 创建忽略数量的流体栈计数映射。插入后须保持键的流体与组件不变。 */
    public static Object2IntMap<FluidStack> createStackCountMap(boolean linked) {
        FluidStackStrategy strategy = FluidStackStrategy.comparingAllButAmount();
        return linked ? new Object2IntLinkedOpenCustomHashMap<>(strategy) : new Object2IntOpenCustomHashMap<>(strategy);
    }
}
