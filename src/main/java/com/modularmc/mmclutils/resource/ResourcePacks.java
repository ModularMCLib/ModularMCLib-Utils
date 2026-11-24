package com.modularmc.mmclutils.resource;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.ResourceProvider;

import java.nio.file.Files;
import java.nio.file.Path;

/** 在原版资源包和生成目录上检查资源位置。文件系统由调用方拥有。 */
public final class ResourcePacks {

    private ResourcePacks() {}

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
        return Files.isRegularFile(ResourcePaths.resolve(root, type, id));
    }
}
