package com.modularmc.mmclutils.api.data;

import com.modularmc.mmclutils.utils.FileUtils.BinaryCodec;
import com.modularmc.mmclutils.utils.ItemUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import com.mojang.serialization.Codec;

import java.io.DataInputStream;
import java.io.IOException;

/** 通过原版编解码器保存组件，并提供旧的标识和数量布局读取器。 */
public final class StackCodecs {

    public static final Codec<ItemStack> ITEM = ItemStack.OPTIONAL_CODEC;
    public static final Codec<FluidStack> FLUID = FluidStack.OPTIONAL_CODEC;

    /** 按命名空间、路径和数量写入。读取时也接受早期版本写出的单 UTF 标识。 */
    public static final BinaryCodec<ItemStack> LEGACY_ITEM = new BinaryCodec<>(input -> {
        Identifier id = readLegacyItemId(input);
        int count = input.readInt();
        if (count < 0) throw new IOException("无效的物品数量：" + count);
        if (count == 0) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.getOptional(id).orElseThrow(() -> new IOException("找不到物品：" + id));
        return new ItemStack(item, count);
    }, (output, stack) -> {
        BinaryCodec.IDENTIFIER_PARTS.write(output, ItemUtils.id(stack));
        output.writeInt(stack.getCount());
    });

    private StackCodecs() {}

    private static Identifier readLegacyItemId(DataInputStream input) throws IOException {
        String first = input.readUTF();
        Identifier id = first.indexOf(':') >= 0 ? Identifier.tryParse(first) : Identifier.tryBuild(first, input.readUTF());
        if (id == null) throw new IOException("无效的物品标识，以 " + first + " 开头");
        return id;
    }
}
