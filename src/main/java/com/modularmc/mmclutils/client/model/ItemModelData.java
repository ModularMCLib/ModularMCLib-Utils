package com.modularmc.mmclutils.client.model;

import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.data.models.ItemModelOutput;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelInstance;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.function.BiConsumer;

/** 生成多层原版物品模型并按图层次序绑定染色来源。 */
public final class ItemModelData {

    private ItemModelData() {}

    public static Identifier generated(Item item, ItemModelOutput items, BiConsumer<Identifier, ModelInstance> models,
                                       Material[] materials, ItemTintSource... tints) {
        if (materials.length == 0 || materials.length != tints.length) {
            throw new IllegalArgumentException("每一层图案须对应一个染色来源");
        }
        var mapping = new TextureMapping();
        var slots = new TextureSlot[materials.length];
        for (int index = 0; index < materials.length; index++) {
            TextureSlot slot = TextureSlot.create("layer" + index);
            slots[index] = slot;
            mapping.put(slot, materials[index]);
        }
        var template = new ModelTemplate(ModelTemplates.FLAT_ITEM.model, ModelTemplates.FLAT_ITEM.suffix, slots);
        Identifier model = template.create(item, mapping, models);
        items.accept(item, ItemModelUtils.tintedModel(model, tints));
        return model;
    }
}
