package com.modularmc.mmclutils.resource;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.util.FileUtil;

import java.nio.file.Path;

/** 将原版资源标识定位到资源包根路径下，遵循原版路径分段校验。 */
public final class ResourcePaths {

    private ResourcePaths() {}

    public static Path resolve(Path root, PackType type, Identifier id) {
        return FileUtil.decomposePath(id.getPath())
                .map(parts -> FileUtil.resolvePath(root.resolve(type.getDirectory()).resolve(id.getNamespace()), parts))
                .getOrThrow();
    }

    public static Path resolve(Path root, Identifier id) {
        return id.resolveAgainst(root);
    }
}
