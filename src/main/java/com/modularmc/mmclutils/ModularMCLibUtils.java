package com.modularmc.mmclutils;

import com.modularmc.mmclutils.api.input.SyncedKeyMappings;
import com.modularmc.mmclutils.client.ModularMCLibUtilsClient;

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

/** 注册双端入口，并提供模组标识、服务器访问和运行环境查询。 */
@Mod(ModularMCLibUtils.MOD_ID)
public class ModularMCLibUtils {

    /** 该模组在加载器和资源标识中使用的命名空间。 */
    public static final String MOD_ID = "mmclutils";
    /** 面向加载器显示的模组名称。 */
    public static final String MOD_NAME = "ModularMCLib Utils";
    /** 用于库自身运行日志的记录器。 */
    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    /**
     * 登记双端共用的模组总线监听器。
     *
     * @param modBus 当前模组的事件总线
     */
    public ModularMCLibUtils(IEventBus modBus) {
        SyncedKeyMappings.init(modBus);
    }

    /**
     * 使用库的命名空间构造资源标识。
     *
     * @param path 标识路径
     * @return 属于 {@link #MOD_ID} 的标识
     */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** 返回当前专用服或本地集成服。服务器尚未启动或已停止时返回空值。 */
    public static @Nullable MinecraftServer getServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    /**
     * 在所属服务器主线程执行命令，并抑制命令反馈。
     *
     * @param server 要执行命令的服务器
     * @param command 不带斜杠前缀的命令文本
     */
    public static void runCommandSilent(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    /** 返回当前游戏实例目录。 */
    public static Path getGameDir() {
        return FMLPaths.GAMEDIR.get();
    }

    /** 判断当前运行是否为生产环境。 */
    public static boolean isProd() {
        return FMLEnvironment.isProduction();
    }

    /** 判断当前运行是否为开发环境。 */
    public static boolean isDev() {
        return !isProd();
    }

    /** 判断加载器是否正在运行数据生成。 */
    public static boolean isDataGen() {
        return DatagenModLoader.isRunningDataGen();
    }

    /**
     * 判断模组是否已加载。在模组列表尚未创建的加载早期使用加载列表查询。
     *
     * @param modId 要查询的命名空间
     * @return 存在对应模组时为 {@code true}
     */
    public static boolean isModLoaded(String modId) {
        ModList mods = ModList.get();
        return mods == null ? FMLLoader.getCurrent().getLoadingModList().getModFileById(modId) != null : mods.isLoaded(modId);
    }

    /** 判断物理运行端。集成服务器所在进程同样返回真。 */
    public static boolean isClientSide() {
        return FMLEnvironment.getDist().isClient();
    }

    /** 判断当前线程是否为物理客户端主线程。专用服务器上始终返回假。 */
    public static boolean isClientThread() {
        return isClientSide() && ModularMCLibUtilsClient.isClientThread();
    }
}
