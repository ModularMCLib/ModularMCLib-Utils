package com.modularmc.mmclutils.entity;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** 按原版装备槽判断一个实体佩戴的物品。 */
public final class EntityEquipment {

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private EntityEquipment() {}

    public static boolean armorMatches(LivingEntity entity, Predicate<ItemStack> condition) {
        for (EquipmentSlot slot : ARMOR) {
            if (!condition.test(entity.getItemBySlot(slot))) return false;
        }
        return true;
    }

    public static int matchingArmorPieces(LivingEntity entity, Predicate<ItemStack> condition) {
        int count = 0;
        for (EquipmentSlot slot : ARMOR) if (condition.test(entity.getItemBySlot(slot))) count++;
        return count;
    }
}
