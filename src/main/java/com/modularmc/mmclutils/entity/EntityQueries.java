package com.modularmc.mmclutils.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Predicate;

/** 使用原版空间索引查询实体，可用半径进一步限制结果。 */
public final class EntityQueries {

    private EntityQueries() {}

    public static <E extends Entity> List<E> within(Level level, Class<E> type, Vec3 center, double radius,
                                                    Predicate<? super E> condition) {
        if (!(radius >= 0)) throw new IllegalArgumentException("半径须为非负数");
        double limit = radius * radius;
        return level.getEntitiesOfClass(type, AABB.ofSize(center, radius * 2, radius * 2, radius * 2),
                entity -> entity.distanceToSqr(center) <= limit && condition.test(entity));
    }
}
