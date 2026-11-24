package com.modularmc.mmclutils.client.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** 通过当前资源版本的原版模型解析器和提交器操作模型。 */
public final class ModelUtils {

    private static final Direction[] DIRECTIONS = Direction.values();

    private ModelUtils() {}

    /** 取得当前资源版本的方块状态模型。资源重载后应重新获取。 */
    public static BlockStateModel block(BlockState state) {
        return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
    }

    /** 按物品模型标识查找当前已加载的模型。 */
    public static ItemModel item(Identifier model) {
        return Minecraft.getInstance().getModelManager().getItemModel(model);
    }

    /** 按已注册键取得独立烘焙模型。键应已参与当前资源版本的模型加载。 */
    public static <T> T standalone(StandaloneModelKey<T> key) {
        return Minecraft.getInstance().getModelManager().getStandaloneModel(key);
    }

    /** 为该栈分配渲染状态并解析显示上下文、世界和持有者驱动的模型。 */
    public static ItemStackRenderState resolveItem(ItemStack stack, ItemDisplayContext context,
                                                   @Nullable Level level, @Nullable ItemOwner owner, int seed) {
        var state = new ItemStackRenderState();
        resolveItem(state, stack, context, level, owner, seed);
        return state;
    }

    /** 用当前模型解析器更新调用方提供的物品渲染状态。世界和持有者可以为空。 */
    public static void resolveItem(ItemStackRenderState output, ItemStack stack, ItemDisplayContext context,
                                   @Nullable Level level, @Nullable ItemOwner owner, int seed) {
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(output, stack, context, level, owner, seed);
    }

    /** 以指定显示上下文、姿态及亮度提交方块模型，提交器负责后续绘制。 */
    public static void submitBlock(BlockState block, BlockDisplayContext context, PoseStack poses, SubmitNodeCollector output,
                                   int light, int overlay, int outlineColor) {
        var state = new BlockModelRenderState();
        Minecraft.getInstance().getBlockModelResolver().update(state, block, context);
        state.submit(poses, output, light, overlay, outlineColor);
    }

    /** 采集一次加权模型选择的全部通用面和各方向剔除面。 */
    public static ObjectList<QuadUtils.Face> quads(BlockStateModel model, RandomSource random) {
        var parts = new ObjectArrayList<BlockStateModelPart>();
        model.collectParts(random, parts);
        return quads(parts);
    }

    /** 使用当前方块视图读取模型。连接纹理等上下文相关模型从视图获取数据。 */
    public static ObjectList<QuadUtils.Face> quads(BlockStateModel model, BlockAndTintGetter level, BlockPos pos,
                                                   BlockState state, RandomSource random) {
        var parts = new ObjectArrayList<BlockStateModelPart>();
        model.collectParts(level, pos, state, random, parts);
        return quads(parts);
    }

    /** 收集所有部件的通用面及六个方向的剔除分组。 */
    public static ObjectList<QuadUtils.Face> quads(List<? extends BlockStateModelPart> parts) {
        var result = new ObjectArrayList<QuadUtils.Face>();
        for (BlockStateModelPart part : parts) {
            for (var quad : part.getQuads(null)) result.add(new QuadUtils.Face(quad, null));
            for (Direction direction : DIRECTIONS) {
                for (var quad : part.getQuads(direction)) result.add(new QuadUtils.Face(quad, direction));
            }
        }
        return new ObjectImmutableList<>(result);
    }

    /** 给由方块实体渲染器负责提交几何的方块提供粒子材质。 */
    public static BlockStateModel particleOnly(Material.Baked material) {
        return new SingleVariant(new ParticlePart(material));
    }

    /** 按输入顺序组合已烘焙模型。各部分共享随机变体种子，粒子材质取第一项。 */
    public static BlockStateModel combine(List<? extends BlockStateModel> parts) {
        if (parts.isEmpty()) throw new IllegalArgumentException("模型组合需要至少一个部件");
        if (parts.size() == 1) return parts.getFirst();
        return new CombinedModel(List.copyOf(parts));
    }

    private record ParticlePart(Material.Baked particleMaterial) implements BlockStateModelPart {

        @Override
        public List<BakedQuad> getQuads(@Nullable Direction direction) {
            return List.of();
        }

        @Override
        public boolean useAmbientOcclusion() {
            return false;
        }

        @Override
        public int materialFlags() {
            return 0;
        }
    }

    private static final class CombinedModel extends DelegateBlockStateModel {

        private final List<BlockStateModel> parts;

        private CombinedModel(List<BlockStateModel> parts) {
            super(parts.getFirst());
            this.parts = parts;
        }

        @Override
        @Deprecated
        public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
            long seed = random.nextLong();
            for (BlockStateModel model : parts) {
                random.setSeed(seed);
                model.collectParts(random, output);
            }
        }

        @Override
        public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                 RandomSource random, List<BlockStateModelPart> output) {
            long seed = random.nextLong();
            for (BlockStateModel model : parts) {
                random.setSeed(seed);
                model.collectParts(level, pos, state, random, output);
            }
        }

        @Override
        @Deprecated
        public int materialFlags() {
            int flags = 0;
            for (BlockStateModel model : parts) flags |= model.materialFlags();
            return flags;
        }

        @Override
        public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
            int flags = 0;
            for (BlockStateModel model : parts) flags |= model.materialFlags(level, pos, state);
            return flags;
        }
    }
}
