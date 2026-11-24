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
        return FileUtil.decomposePath(id.getPath())
                .map(parts -> FileUtil.resolvePath(root.resolve(type.getDirectory()).resolve(id.getNamespace()), parts))
                .getOrThrow();
    }

    public static Path resolve(Path root, Identifier id) {
        return id.resolveAgainst(root);
    }
}
