package com.modularmc.mmclutils.client;

import com.modularmc.mmclutils.ModularMCLibUtils;
import com.modularmc.mmclutils.client.input.ClientKeyBindings;
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

/**
 * 注册仅在物理客户端加载的渲染与输入监听，并提供当前客户端状态查询。
 * 客户端方法只能从拥有客户端类的物理客户端调用。
 */
@Mod(value = ModularMCLibUtils.MOD_ID, dist = Dist.CLIENT)
public final class ModularMCLibUtilsClient {

    /**
     * 登记客户端模组总线监听器。
     *
     * @param modBus 当前模组的事件总线
     */
    public ModularMCLibUtilsClient(IEventBus modBus) {
        EffectRenderer.init(modBus);
        ClientKeyBindings.init(modBus);
    }

    /** 判断当前线程是否为客户端主线程。 */
    public static boolean isClientThread() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.isSameThread();
    }

    /** 判断实体是否由客户端实体渲染器绘制。 */
    public static boolean isClientPlayer(Entity entity) {
        return entity instanceof AbstractClientPlayer;
    }

    /** 返回当前本地玩家，尚未进入世界时返回空值。 */
    public static @Nullable LocalPlayer getPlayer() {
        return Minecraft.getInstance().player;
    }

    /** 返回当前本地玩家的 UUID，尚未进入世界时返回空值。 */
    public static @Nullable UUID getPlayerId() {
        LocalPlayer player = getPlayer();
        return player == null ? null : player.getUUID();
    }

    /** 返回当前客户端世界，尚未进入世界时返回空值。 */
    public static @Nullable ClientLevel getLevel() {
        return Minecraft.getInstance().level;
    }
}
