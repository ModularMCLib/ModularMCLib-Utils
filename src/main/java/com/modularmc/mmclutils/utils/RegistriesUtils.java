package com.modularmc.mmclutils.utils;

import com.modularmc.mmclutils.api.function.Memoization;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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

    /** 按完整标识查询原版物品注册表。 */
    public static Item getItem(ResourceLocation id) {
        return getValue(BuiltInRegistries.ITEM, id);
    }

    /** 解析文本标识后查询物品。省略命名空间时使用原版的默认命名空间。 */
    public static Item getItem(String id) {
        return getItem(ResourceLocation.parse(id));
    }

    /** 由命名空间和路径构造标识后查询物品。 */
    public static Item getItem(String namespace, String path) {
        return getItem(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    /** 创建指定物品和数量的新栈。标识须已注册。 */
    public static ItemStack getItemStack(ResourceLocation id, int count) {
        return new ItemStack(getItem(id), count);
    }

    /** 解析后用指定数量创建物品栈。 */
    public static ItemStack getItemStack(String id, int count) {
        return getItemStack(ResourceLocation.parse(id), count);
    }

    /** 解析后创建数量为一的物品栈。 */
    public static ItemStack getItemStack(String id) {
        return getItemStack(id, 1);
    }

    /** 按完整标识查询原版方块注册表。 */
    public static Block getBlock(ResourceLocation id) {
        return getValue(BuiltInRegistries.BLOCK, id);
    }

    /** 解析文本标识后查询方块。 */
    public static Block getBlock(String id) {
        return getBlock(ResourceLocation.parse(id));
    }

    /** 首次取值时查找方块，成功后复用同一结果。 */
    public static Supplier<Block> getSupplierBlock(String id) {
        ResourceLocation blockId = ResourceLocation.parse(id);
        return Memoization.memoize(() -> getBlock(blockId));
    }

    /** 按完整标识查询原版流体注册表。 */
    public static Fluid getFluid(ResourceLocation id) {
        return getValue(BuiltInRegistries.FLUID, id);
    }

    /** 解析文本标识后查询流体。 */
    public static Fluid getFluid(String id) {
        return getFluid(ResourceLocation.parse(id));
    }

    /** amount 以毫桶计量。 */
    public static FluidStack getFluidStack(ResourceLocation id, int amount) {
        return new FluidStack(getFluid(id), amount);
    }

    /** 解析标识后创建指定毫桶数的流体栈。 */
    public static FluidStack getFluidStack(String id, int amount) {
        return getFluidStack(ResourceLocation.parse(id), amount);
    }

    /** 按给定顺序构造注册键，调用方可在对应注册表中查询。 */
    public static <T> List<ResourceKey<T>> resolveResourceKeys(ResourceKey<Registry<T>> registryKey,
                                                               String... locations) {
        return Arrays.stream(locations)
                .map(location -> ResourceKey.create(registryKey, ResourceLocation.parse(location)))
                .toList();
    }

    private static <T> T getValue(Registry<T> registry, ResourceLocation id) {
        return registry.getOptional(id)
                .orElseThrow(() -> new IllegalArgumentException("注册表 " + registry.key().location() + " 缺少标识 " + id));
    }
}
