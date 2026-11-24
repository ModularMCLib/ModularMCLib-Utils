package com.modularmc.mmclutils.utils;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/** 查询附近实体与装备，并通过原版流程传送实体。 */
public final class EntityUtils {

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private EntityUtils() {}

    public static <E extends Entity> List<E> within(Level level, Class<E> type, Vec3 center, double radius,
                                                    Predicate<? super E> condition) {
        if (!(radius >= 0)) throw new IllegalArgumentException("半径须为非负数");
        double limit = radius * radius;
        return level.getEntitiesOfClass(type, AABB.ofSize(center, radius * 2, radius * 2, radius * 2),
                entity -> entity.distanceToSqr(center) <= limit && condition.test(entity));
    }

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

    public static @Nullable Entity teleport(Entity entity, ServerLevel level, Vec3 position) {
        return teleport(entity, level, position, entity.getYRot(), entity.getXRot());
    }

    /** 在服务端线程传送实体，保留既有速度。返回原版传送后的实体，失败时返回空值。 */
    public static @Nullable Entity teleport(Entity entity, ServerLevel level, Vec3 position, float yaw, float pitch) {
        return entity.teleport(new TeleportTransition(level, position, entity.getDeltaMovement(), yaw,
                pitch, TeleportTransition.DO_NOTHING));
    }

    /** 按维度键定位目标维度。维度尚未加载时返回空值。 */
    public static @Nullable Entity teleport(Entity entity, MinecraftServer server, ResourceKey<Level> dimension, Vec3 position) {
        ServerLevel level = server.getLevel(dimension);
        return level == null ? null : teleport(entity, level, position);
    }
}
