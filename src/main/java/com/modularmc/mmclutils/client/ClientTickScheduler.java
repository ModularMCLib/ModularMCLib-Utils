package com.modularmc.mmclutils.client;

import com.modularmc.mmclutils.ModularMCLibUtils;
import com.modularmc.mmclutils.api.schedule.Schedulers;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import org.jetbrains.annotations.ApiStatus;

/** 将客户端 Post Tick 与断开事件转交给客户端调度车道。 */
@ApiStatus.Internal
@EventBusSubscriber(modid = ModularMCLibUtils.MOD_ID, value = Dist.CLIENT)
public final class ClientTickScheduler {

    private ClientTickScheduler() {}

    @SubscribeEvent
    private static void onTick(ClientTickEvent.Post event) {
        Schedulers.runClientTick();
    }

    @SubscribeEvent
    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Schedulers.resetClient();
    }
}
