package com.modularmc.mmclutils.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/** 通过原版注册表构造类型安全的标签键。 */
public final class TagKeys {

    private TagKeys() {}

    public static TagKey<Block> block(Identifier id) {
        return TagKey.create(Registries.BLOCK, id);
    }

    public static TagKey<Item> item(Identifier id) {
        return TagKey.create(Registries.ITEM, id);
    }

    public static TagKey<Fluid> fluid(Identifier id) {
        return TagKey.create(Registries.FLUID, id);
    }
}
