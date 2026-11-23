package com.modularmc.mmclutils.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/** 通过原版传送流程更换实体所在的维度和坐标。 */
public final class EntityTeleports {

    private EntityTeleports() {}

    public static @Nullable Entity teleport(Entity entity, ServerLevel level, Vec3 position) {
        return entity.teleport(new TeleportTransition(level, position, entity.getDeltaMovement(), entity.getYRot(),
                entity.getXRot(), TeleportTransition.DO_NOTHING));
    }
}
