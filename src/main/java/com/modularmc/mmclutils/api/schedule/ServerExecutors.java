package com.modularmc.mmclutils.api.schedule;

import com.modularmc.mmclutils.ModularMCLibUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

/** 每台服务器按标识共享的优先计算线程，停服时取消该服务器的任务。 */
@EventBusSubscriber(modid = ModularMCLibUtils.MOD_ID)
public final class ServerExecutors {

    private static final Reference2ObjectOpenHashMap<MinecraftServer, Object2ObjectOpenHashMap<ResourceLocation, PriorityTaskExecutor>> SERVERS = new Reference2ObjectOpenHashMap<>();

    private ServerExecutors() {}

    /** 在所属服务器线程上取得执行器。后台只应使用与游戏线程分离的数据。 */
    public static synchronized PriorityTaskExecutor priority(MinecraftServer server, ResourceLocation id) {
        if (!server.isSameThread()) throw new IllegalStateException("须在所属服务器线程取得执行器");
        return SERVERS.computeIfAbsent(server, ignored -> new Object2ObjectOpenHashMap<>())
                .computeIfAbsent(id, key -> new PriorityTaskExecutor(key.toString()));
    }

    @SubscribeEvent
    private static void release(ServerStoppedEvent event) {
        Object2ObjectOpenHashMap<ResourceLocation, PriorityTaskExecutor> executors;
        synchronized (ServerExecutors.class) {
            executors = SERVERS.remove(event.getServer());
        }
        if (executors != null) executors.values().forEach(PriorityTaskExecutor::close);
    }
}
