package com.modularmc.mmclutils.client.model;

import com.modularmc.mmclutils.client.render.quad.QuadMeshes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

/** 通过当前资源版本的原版模型解析器和提交器操作模型。 */
public final class ClientModels {

    private static final Direction[] DIRECTIONS = Direction.values();

    private ClientModels() {}

    public static BlockStateModel block(BlockState state) {
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
    }

    public static ItemModel item(Identifier model) {
        return Minecraft.getInstance().getModelManager().getItemModel(model);
    }

    public static <T> T standalone(StandaloneModelKey<T> key) {
        return Minecraft.getInstance().getModelManager().getStandaloneModel(key);
    }

    public static ItemStackRenderState resolveItem(ItemStack stack, ItemDisplayContext context,
                                                   @Nullable Level level, @Nullable ItemOwner owner, int seed) {
        var state = new ItemStackRenderState();
        resolveItem(state, stack, context, level, owner, seed);
        return state;
    }

    public static void resolveItem(ItemStackRenderState output, ItemStack stack, ItemDisplayContext context,
                                   @Nullable Level level, @Nullable ItemOwner owner, int seed) {
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(output, stack, context, level, owner, seed);
    }

    public static void submitBlock(BlockState block, BlockDisplayContext context, PoseStack poses, SubmitNodeCollector output,
                                   int light, int overlay, int outlineColor) {
        var state = new BlockModelRenderState();
        Minecraft.getInstance().getBlockModelResolver().update(state, block, context);
        state.submit(poses, output, light, overlay, outlineColor);
    }

    /** 采集一次加权模型选择的全部通用面和各方向剔除面。 */
    public static ObjectList<QuadMeshes.Face> quads(BlockStateModel model, RandomSource random) {
        var parts = new ObjectArrayList<BlockStateModelPart>();
        model.collectParts(random, parts);
        var result = new ObjectArrayList<QuadMeshes.Face>();
        for (BlockStateModelPart part : parts) {
            for (var quad : part.getQuads(null)) result.add(new QuadMeshes.Face(quad, null));
            for (Direction direction : DIRECTIONS) {
                for (var quad : part.getQuads(direction)) result.add(new QuadMeshes.Face(quad, direction));
            }
        }
        return new ObjectImmutableList<>(result);
    }
}
