package com.modularmc.mmclutils.utils;

import com.modularmc.mmclutils.api.inventory.ItemStackStrategy;
import com.modularmc.mmclutils.utils.FileUtils.Serializer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
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
        Identifier id = readLegacyItemId(input);
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

    private static Identifier readLegacyItemId(DataInputStream input) throws IOException {
        String first = input.readUTF();
        Identifier id = first.indexOf(':') >= 0 ? Identifier.tryParse(first) : Identifier.tryBuild(first, input.readUTF());
        if (id == null) throw new IOException("无效的物品标识，以 " + first + " 开头");
        return id;
    }

    public static Identifier id(ItemStack stack) {
        return id(stack.getItem());
    }

    public static Identifier id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    /** 返回方块对应的物品标识。 */
    public static Identifier id(Block block) {
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

    public static ItemStack copyFirst(ItemStack... stacks) {
        return firstNonEmpty(stacks).copy();
    }

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
