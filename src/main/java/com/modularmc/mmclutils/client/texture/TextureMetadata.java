package com.modularmc.mmclutils.client.texture;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.util.Optional;

/** 通过资源提供器读取指定的原版贴图元数据段。 */
public final class TextureMetadata {

    private TextureMetadata() {}

    /** 将精灵标识映射为资源包内的 PNG 文件标识。 */
    public static ResourceLocation textureFile(ResourceLocation id) {
        return id.withPath(path -> "textures/" + path + ".png");
    }

    /** 按精灵的源贴图查找指定元数据，资源或元数据段缺失时返回空结果。 */
    public static <T> Optional<T> readSprite(ResourceManager resources, TextureAtlasSprite sprite, MetadataSectionSerializer<T> type)
                                                                                                                                      throws IOException {
        return read(resources, textureFile(sprite.contents().name()), type);
    }

    /** 读取当前资源优先级下的贴图元数据。资源缺失时返回空结果，读取失败时传播 I/O 异常。 */
    public static <T> Optional<T> read(ResourceManager resources, ResourceLocation file, MetadataSectionSerializer<T> type) throws IOException {
        var resource = resources.getResource(file);
        return resource.isPresent() ? resource.get().metadata().getSection(type) : Optional.empty();
    }
}
