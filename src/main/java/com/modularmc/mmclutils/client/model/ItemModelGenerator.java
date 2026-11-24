package com.modularmc.mmclutils.client.model;

import net.minecraft.client.color.item.ItemColor;
import net.minecraft.data.models.model.ModelLocationUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** 生成多层原版物品模型并按图层次序绑定染色来源。 */
public final class ItemModelGenerator {

    private ItemModelGenerator() {}

    /** 登记原版平面物品模型，贴图按从底层到顶层排列。原版生成器支持一至五层。 */
    public static ResourceLocation generated(Item item, BiConsumer<ResourceLocation, Supplier<JsonElement>> models,
                                             ResourceLocation... textures) {
        if (textures.length == 0 || textures.length > 5) throw new IllegalArgumentException("物品模型须包含一至五层贴图");
        ResourceLocation model = ModelLocationUtils.getModelLocation(item);
        ResourceLocation[] layers = textures.clone();
        models.accept(model, () -> {
            var output = new JsonObject();
            output.addProperty("parent", "minecraft:item/generated");
            var mapping = new JsonObject();
            for (int index = 0; index < layers.length; index++) mapping.addProperty("layer" + index, layers[index].toString());
            output.add("textures", mapping);
            return output;
        });
        return model;
    }

    /** 在客户端颜色注册时按图层登记染色回调。未登记染色的图层保持原色。 */
    public static void registerTints(RegisterColorHandlersEvent.Item event, Item item, ItemColor... tints) {
        ItemColor[] layers = tints.clone();
        event.register((stack, index) -> index >= 0 && index < layers.length ? layers[index].getColor(stack, index) : -1, item);
    }
}
