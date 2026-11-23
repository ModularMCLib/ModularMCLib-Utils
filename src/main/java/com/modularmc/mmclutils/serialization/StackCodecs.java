package com.modularmc.mmclutils.serialization;

import com.modularmc.mmclutils.item.ItemStacks;
import com.modularmc.mmclutils.serialization.BinaryCodecs.BinaryCodec;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import com.mojang.serialization.Codec;

import java.io.IOException;

/** 通过原版编解码器保存组件，并提供旧的标识和数量布局读取器。 */
public final class StackCodecs {

    public static final Codec<ItemStack> ITEM = ItemStack.OPTIONAL_CODEC;
    public static final Codec<FluidStack> FLUID = FluidStack.OPTIONAL_CODEC;

    public static final BinaryCodec<ItemStack> LEGACY_ITEM = new BinaryCodec<>(input -> {
        var id = BinaryCodecs.IDENTIFIER.read(input);
        int count = input.readInt();
        if (count < 0) throw new IOException("无效的物品数量：" + count);
        if (count == 0) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.getOptional(id).orElseThrow(() -> new IOException("找不到物品：" + id));
        return new ItemStack(item, count);
    }, (output, stack) -> {
        BinaryCodecs.IDENTIFIER.write(output, ItemStacks.id(stack));
        output.writeInt(stack.getCount());
    });

    private StackCodecs() {}
}
