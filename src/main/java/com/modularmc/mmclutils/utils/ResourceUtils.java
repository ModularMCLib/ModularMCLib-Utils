package com.modularmc.mmclutils.utils;

import net.minecraft.FileUtil;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.ResourceProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 在原版资源包和生成目录上定位和查询资源。文件系统由调用方拥有。 */
public final class ResourceUtils {

    private ResourceUtils() {}

    /**
     * 将目录视为原版资源包，目录应包含 assets 或 data 等资源类型目录。
     *
     * @param pack 资源包的原版位置元数据
     * @param root 资源包根目录
     * @return 可供原版读取的路径资源包
     */
    public static PathPackResources openPath(PackLocationInfo pack, Path root) {
        return new PathPackResources(pack, root);
    }

    /** 查询资源包中是否存在指定资源，不打开资源流。 */
    public static boolean exists(PackResources pack, PackType type, ResourceLocation id) {
        return pack.getResource(type, id) != null;
    }

    /** 从当前资源提供器查询标识是否可读。 */
    public static boolean exists(ResourceProvider provider, ResourceLocation id) {
        return provider.getResource(id).isPresent();
    }

    /** 查询资源标识对应的普通文件是否存在，根目录包含资源类型子目录。 */
    public static boolean exists(Path root, PackType type, ResourceLocation id) {
        return Files.isRegularFile(resolve(root, type, id));
    }

    /** 以包含资源类型子目录的包根目录解析完整资源路径。 */
    public static Path resolve(Path root, PackType type, ResourceLocation id) {
        return resolve(root.resolve(type.getDirectory()), id);
    }

    /** 以资源类型目录为根，按命名空间和标识中的各路径片段解析。非法路径会抛出异常。 */
    public static Path resolve(Path root, ResourceLocation id) {
        return FileUtil.resolvePath(root.resolve(id.getNamespace()), resourcePath(id));
    }

    /** 将标识拆为命名空间和路径片段，可用于资源索引。 */
    public static String[] pathSegments(ResourceLocation id) {
        return pathSegments(id, 1);
    }

    /** 在命名空间前附加资源包类型对应的目录。 */
    public static String[] pathSegments(PackType type, ResourceLocation id) {
        String[] segments = pathSegments(id, 2);
        segments[0] = type.getDirectory();
        return segments;
    }

    /** 校验可用作资源索引的相对路径及其每个文件名。 */
    public static boolean isValidResourcePath(Path path) {
        if (path.getRoot() != null || path.getNameCount() == 0) return false;
        for (Path segment : path) {
            String name = segment.toString();
            if (name.equals(".") || name.equals("..") || !FileUtil.isValidStrictPathSegment(name)) return false;
        }
        return true;
    }

    private static String[] pathSegments(ResourceLocation id, int prefixLength) {
        List<String> parts = resourcePath(id);
        String[] result = new String[prefixLength + parts.size()];
        result[prefixLength - 1] = id.getNamespace();
        for (int index = 0; index < parts.size(); index++) result[prefixLength + index] = parts.get(index);
        return result;
    }

    private static List<String> resourcePath(ResourceLocation id) {
        FileUtil.validatePath(id.getNamespace());
        return FileUtil.decomposePath(id.getPath()).getOrThrow();
    }
}
