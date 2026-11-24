package com.modularmc.mmclutils.utils;

import com.modularmc.mmclutils.api.function.Memoization;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import org.jspecify.annotations.NullMarked;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/** 查询已注册的原版类型。找不到指定标识时抛出 IllegalArgumentException。 */
@NullMarked
public final class RegistriesUtils {

    private RegistriesUtils() {}

    public static Item getItem(Identifier id) {
        return getValue(BuiltInRegistries.ITEM, id);
    }

    public static Item getItem(String id) {
        return getItem(Identifier.parse(id));
    }

    public static Item getItem(String namespace, String path) {
        return getItem(Identifier.fromNamespaceAndPath(namespace, path));
    }

    public static ItemStack getItemStack(Identifier id, int count) {
        return new ItemStack(getItem(id), count);
    }

    public static ItemStack getItemStack(String id, int count) {
        return getItemStack(Identifier.parse(id), count);
    }

    public static ItemStack getItemStack(String id) {
        return getItemStack(id, 1);
    }

    public static Block getBlock(Identifier id) {
        return getValue(BuiltInRegistries.BLOCK, id);
    }

    public static Block getBlock(String id) {
        return getBlock(Identifier.parse(id));
    }

    /** 首次取值时查找方块，成功后复用同一结果。 */
    public static Supplier<Block> getSupplierBlock(String id) {
        Identifier blockId = Identifier.parse(id);
        return Memoization.memoize(() -> getBlock(blockId));
    }

    public static Fluid getFluid(Identifier id) {
        return getValue(BuiltInRegistries.FLUID, id);
    }

    public static Fluid getFluid(String id) {
        return getFluid(Identifier.parse(id));
    }

    /** amount 以毫桶计量。 */
    public static FluidStack getFluidStack(Identifier id, int amount) {
        return new FluidStack(getFluid(id), amount);
    }

    public static FluidStack getFluidStack(String id, int amount) {
        return getFluidStack(Identifier.parse(id), amount);
    }

    /** 按给定顺序构造注册键，调用方可在对应注册表中查询。 */
    public static <T> List<ResourceKey<T>> resolveResourceKeys(ResourceKey<Registry<T>> registryKey,
                                                               String... locations) {
        return Arrays.stream(locations)
                .map(location -> ResourceKey.create(registryKey, Identifier.parse(location)))
                .toList();
    }

    private static <T> T getValue(Registry<T> registry, Identifier id) {
        return registry.getOptional(id)
                .orElseThrow(() -> new IllegalArgumentException("注册表 " + registry.key().identifier() + " 缺少标识 " + id));
    }
}
