package com.modularmc.mmclutils.utils;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
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

    /**
     * 在所属逻辑线程上查询球形范围内的实体。距离以实体位置计算。
     *
     * @param level     用于查询的世界
     * @param type      允许返回的实体类型
     * @param center    球心的世界坐标
     * @param radius    非负半径，单位为方块
     * @param condition 对范围内候选的附加筛选
     * @param <E>       实体类型
     * @return 通过筛选的实体
     */
    public static <E extends Entity> List<E> within(Level level, Class<E> type, Vec3 center, double radius,
                                                    Predicate<? super E> condition) {
        if (!(radius >= 0)) throw new IllegalArgumentException("半径须为非负数");
        double limit = radius * radius;
        return level.getEntitiesOfClass(type, AABB.ofSize(center, radius * 2, radius * 2, radius * 2),
                entity -> entity.distanceToSqr(center) <= limit && condition.test(entity));
    }

    /**
     * 按装备槽类型及组内索引查询原版装备槽。
     *
     * @param type  装备槽所属类型
     * @param index 该类型中的索引
     * @return 对应的装备槽
     * @throws IllegalArgumentException 指定类型与索引组合不存在
     */
    public static EquipmentSlot equipmentSlotByTypeAndIndex(EquipmentSlot.Type type, int index) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == type && slot.getIndex() == index) return slot;
        }
        throw new IllegalArgumentException("无对应装备槽 " + type + " " + index);
    }

    /** 检查四个盔甲槽，谓词也会接收空栈。 */
    public static boolean armorMatches(LivingEntity entity, Predicate<ItemStack> condition) {
        for (EquipmentSlot slot : ARMOR) {
            if (!condition.test(entity.getItemBySlot(slot))) return false;
        }
        return true;
    }

    /** 统计四个盔甲槽中符合条件的数量，谓词也会接收空栈。 */
    public static int matchingArmorPieces(LivingEntity entity, Predicate<ItemStack> condition) {
        int count = 0;
        for (EquipmentSlot slot : ARMOR) if (condition.test(entity.getItemBySlot(slot))) count++;
        return count;
    }

    /** 在服务端线程传送实体，保留其朝向和速度。失败时返回空值。 */
    public static @Nullable Entity teleport(Entity entity, ServerLevel level, Vec3 position) {
        return teleport(entity, level, position, entity.getYRot(), entity.getXRot());
    }

    /** 在服务端线程传送实体，保留既有速度。返回原版传送后的实体，失败时返回空值。 */
    public static @Nullable Entity teleport(Entity entity, ServerLevel level, Vec3 position, float yaw, float pitch) {
        return entity.changeDimension(new DimensionTransition(level, position, entity.getDeltaMovement(), yaw,
                pitch, DimensionTransition.DO_NOTHING));
    }

    /** 按维度键定位目标维度。维度尚未加载时返回空值。 */
    public static @Nullable Entity teleport(Entity entity, MinecraftServer server, ResourceKey<Level> dimension, Vec3 position) {
        ServerLevel level = server.getLevel(dimension);
        return level == null ? null : teleport(entity, level, position);
    }
}
