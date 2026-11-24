package com.modularmc.mmclutils.api.recipe;

import net.minecraft.world.item.crafting.Ingredient;

/**
 * 以物品谓词和消耗数量表达一项配方需求。
 *
 * <p>
 * 实现方负责输入校验与存储。匹配器可以先合并同类物品，再据此契约计算实际槽位用量。
 * </p>
 */
public interface CountedItemRequirement {

    /**
     * 返回对归并后的物品栈生效的匹配谓词。
     */
    Ingredient ingredient();

    /**
     * 返回该项需求消耗的正数物品数量。
     */
    int count();
}
