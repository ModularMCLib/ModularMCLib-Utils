package com.modularmc.mmclutils;

import com.modularmc.mmclutils.client.ModularMCLibUtilsClient;
import com.modularmc.mmclutils.input.SyncedKeyMappings;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.data.loading.DatagenModLoader;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

@Mod(ModularMCLibUtils.MOD_ID)
public class ModularMCLibUtils {

    public static final String MOD_ID = "mmclutils";
    public static final String MOD_NAME = "ModularMCLib Utils";
    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    public ModularMCLibUtils(IEventBus modBus) {
        SyncedKeyMappings.init(modBus);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** 返回当前专用服或本地集成服。服务器尚未启动或已停止时返回空值。 */
    public static @Nullable MinecraftServer getMinecraftServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    /** 在传入服务器的主线程上执行命令，抑制命令反馈。 */
    public static void runCommandSilently(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    public static Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    public static boolean isProd() {
        return FMLEnvironment.isProduction();
    }

    public static boolean isDev() {
        return !isProd();
    }

    public static boolean isDataGen() {
        return DatagenModLoader.isRunningDataGen();
    }

    public static boolean isModLoaded(String modId) {
        ModList mods = ModList.get();
        return mods == null ? FMLLoader.getCurrent().getLoadingModList().getModFileById(modId) != null : mods.isLoaded(modId);
    }

    /** 判断物理运行端。集成服务器所在进程同样返回真。 */
    public static boolean isClientSide() {
        return FMLEnvironment.getDist().isClient();
    }

    public static boolean isClientThread() {
        return isClientSide() && ModularMCLibUtilsClient.isClientThread();
    }
}
