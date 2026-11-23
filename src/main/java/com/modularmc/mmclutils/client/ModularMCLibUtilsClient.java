package com.modularmc.mmclutils.client;

import com.modularmc.mmclutils.ModularMCLibUtils;
import com.modularmc.mmclutils.client.render.effect.EffectRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** 注册仅在物理客户端加载的入口。 */
@Mod(value = ModularMCLibUtils.MOD_ID, dist = Dist.CLIENT)
public final class ModularMCLibUtilsClient {

    public ModularMCLibUtilsClient(IEventBus modBus) {
        EffectRenderer.init(modBus);
    }

    public static boolean isClientThread() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.isSameThread();
    }

    public static boolean isClientPlayer(Entity entity) {
        return entity instanceof AbstractClientPlayer;
    }

    public static @Nullable LocalPlayer getPlayer() {
        return Minecraft.getInstance().player;
    }

    public static @Nullable UUID getPlayerId() {
        LocalPlayer player = getPlayer();
        return player == null ? null : player.getUUID();
    }

    public static @Nullable ClientLevel getLevel() {
        return Minecraft.getInstance().level;
    }
}
