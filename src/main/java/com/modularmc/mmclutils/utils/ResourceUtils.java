package com.modularmc.mmclutils.utils;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.FileUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 在原版资源包和生成目录上定位和查询资源。文件系统由调用方拥有。 */
public final class ResourceUtils {

    private ResourceUtils() {}

    public static PathPackResources openPath(PackLocationInfo pack, Path root) {
        return new PathPackResources(pack, root);
    }

    public static boolean exists(PackResources pack, PackType type, Identifier id) {
        return pack.getResource(type, id) != null;
    }

    public static boolean exists(ResourceProvider provider, Identifier id) {
        return provider.getResource(id).isPresent();
    }

    public static boolean exists(Path root, PackType type, Identifier id) {
        return Files.isRegularFile(resolve(root, type, id));
    }

    public static Path resolve(Path root, PackType type, Identifier id) {
        return resolve(root.resolve(type.getDirectory()), id);
    }

    public static Path resolve(Path root, Identifier id) {
        return FileUtil.resolvePath(root.resolve(id.getNamespace()), resourcePath(id));
    }

    /** 将标识拆为命名空间和路径片段，可用于资源索引。 */
    public static String[] pathSegments(Identifier id) {
        return pathSegments(id, 1);
    }

    /** 在命名空间前附加资源包类型对应的目录。 */
    public static String[] pathSegments(PackType type, Identifier id) {
        String[] segments = pathSegments(id, 2);
        segments[0] = type.getDirectory();
        return segments;
    }

    /** 校验可用作资源索引的相对路径及其每个文件名。 */
    public static boolean isValidResourcePath(Path path) {
        if (path.getRoot() != null || path.getNameCount() == 0) return false;
        for (Path segment : path) {
            String name = segment.toString();
            if (name.isEmpty() || !FileUtil.isValidPathSegment(name)) return false;
        }
        return true;
    }

    private static String[] pathSegments(Identifier id, int prefixLength) {
        List<String> parts = resourcePath(id);
        String[] result = new String[prefixLength + parts.size()];
        result[prefixLength - 1] = id.getNamespace();
        for (int index = 0; index < parts.size(); index++) result[prefixLength + index] = parts.get(index);
        return result;
    }

    private static List<String> resourcePath(Identifier id) {
        FileUtil.validatePath(id.getNamespace());
        return FileUtil.decomposePath(id.getPath()).getOrThrow();
    }
}
