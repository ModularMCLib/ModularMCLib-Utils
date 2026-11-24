package com.modularmc.mmclutils.utils;

import com.modularmc.mmclutils.api.inventory.ItemStackStrategy;
import com.modularmc.mmclutils.utils.FileUtils.Serializer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;

/** 查询标识、比较、拆分和计数物品栈，保留物品组件。 */
public final class ItemUtils {

    /** 旧文件格式依次写入命名空间、路径和数量。读取时也接受单个 UTF 字段的完整标识。 */
    public static final Serializer<ItemStack> SERIALIZER = new Serializer<>(input -> {
        ResourceLocation id = readLegacyItemId(input);
        int count = input.readInt();
        if (count < 0) throw new IOException("无效的物品数量：" + count);
        if (count == 0) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.getOptional(id).orElseThrow(() -> new IOException("找不到物品：" + id));
        return new ItemStack(item, count);
    }, (output, stack) -> {
        Serializer.IDENTIFIER_PARTS.write(output, id(stack));
        output.writeInt(stack.getCount());
    });

    private ItemUtils() {}

    private static ResourceLocation readLegacyItemId(DataInputStream input) throws IOException {
        String first = input.readUTF();
        ResourceLocation id = first.indexOf(':') >= 0 ? ResourceLocation.tryParse(first) : ResourceLocation.tryBuild(first, input.readUTF());
        if (id == null) throw new IOException("无效的物品标识，以 " + first + " 开头");
        return id;
    }

    /** 返回栈中物品的注册表标识，空栈使用空气物品的标识。 */
    public static ResourceLocation id(ItemStack stack) {
        return id(stack.getItem());
    }

    /** 返回已注册物品的标识。 */
    public static ResourceLocation id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    /** 返回方块对应的物品标识。 */
    public static ResourceLocation id(Block block) {
        return id(block.asItem());
    }

    /** 生成包含数量和标识的调试文本，标识使用原版文件名格式。 */
    public static String formatStack(ItemStack stack) {
        return stack.getCount() + "x_" + id(stack).toDebugFileName();
    }

    /** 取得第一个非空栈本身，所有候选栈为空时返回 ItemStack.EMPTY。 */
    public static ItemStack firstNonEmpty(ItemStack... stacks) {
        if (stacks.length == 0) throw new IllegalArgumentException("至少需要一个候选物品栈");
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** 复制第一个非空栈及其数据组件，候选都为空时返回空栈。 */
    public static ItemStack copyFirst(ItemStack... stacks) {
        return firstNonEmpty(stacks).copy();
    }

    /** 复制第一个非空栈并使用指定数量，候选都为空时返回空栈。 */
    public static ItemStack copyFirst(int count, ItemStack... stacks) {
        return firstNonEmpty(stacks).copyWithCount(count);
    }

    /** 判断物品栈能否均分成指定数量的非空份。 */
    public static boolean isCountDivisible(ItemStack stack, int divisor) {
        if (divisor <= 0) throw new IllegalArgumentException("物品份数须为正数");
        int count = stack.getCount();
        return count >= divisor && count % divisor == 0;
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

    /** 同时比较物品类型、数据组件和堆叠数量。 */
    public static boolean matches(ItemStack first, ItemStack second) {
        return first.getCount() == second.getCount() && ItemStack.isSameItemSameComponents(first, second);
    }

    /** 按物品与组件汇总非空栈的数量，不保留输入顺序。 */
    public static Object2IntMap<ItemStack> countStacks(Iterable<ItemStack> inputs) {
        return countStacks(inputs, false);
    }

    /** 按物品与组件汇总非空栈的数量。键是数量为一的副本，可保留同类栈首次出现的顺序。总量超出 int 范围时抛出算术异常。 */
    public static Object2IntMap<ItemStack> countStacks(Iterable<ItemStack> inputs, boolean linked) {
        var counts = createStackCountMap(linked);
        for (ItemStack stack : inputs) {
            if (!stack.isEmpty()) counts.mergeInt(stack.copyWithCount(1), stack.getCount(), Math::addExact);
        }
        return counts;
    }

    /** 创建忽略栈数量比较的计数映射。插入后须保持键的物品与组件不变。 */
    public static Object2IntMap<ItemStack> createStackCountMap(boolean linked) {
        ItemStackStrategy strategy = ItemStackStrategy.comparingAllButCount();
        return linked ? new Object2IntLinkedOpenCustomHashMap<>(strategy) : new Object2IntOpenCustomHashMap<>(strategy);
    }
}
