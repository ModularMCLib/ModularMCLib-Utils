package com.modularmc.mmclutils.client.render.texture;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.util.Optional;

/** 通过资源提供器读取指定的原版贴图元数据段。 */
public final class TextureMetadata {

    private TextureMetadata() {}

    public static Identifier textureFile(Identifier id) {
        return id.withPath(path -> "textures/" + path + ".png");
    }

    public static <T> Optional<T> readSprite(ResourceManager resources, TextureAtlasSprite sprite, MetadataSectionType<T> type)
                                                                                                                                throws IOException {
        return read(resources, textureFile(sprite.contents().name()), type);
    }

    public static <T> Optional<T> read(ResourceManager resources, Identifier file, MetadataSectionType<T> type) throws IOException {
        var resource = resources.getResource(file);
        return resource.isPresent() ? resource.get().metadata().getSection(type) : Optional.empty();
    }
}
