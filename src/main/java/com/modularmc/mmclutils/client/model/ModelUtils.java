package com.modularmc.mmclutils.client.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;

/** 通过当前资源版本的原版烘焙模型解析器和渲染器操作模型。 */
public final class ModelUtils {

    private static final Direction[] DIRECTIONS = Direction.values();

    private ModelUtils() {}

    /** 取得当前资源版本的方块状态模型。资源重载后应重新获取。 */
    public static BakedModel block(BlockState state) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
    }

    /** 按物品注册标识查找当前已加载的模型。 */
    public static BakedModel item(ResourceLocation model) {
        return Minecraft.getInstance().getModelManager().getModel(ModelResourceLocation.inventory(model));
    }

    /** 取得通过 ModelEvent.RegisterAdditional 登记的独立模型。 */
    public static BakedModel standalone(ResourceLocation model) {
        return Minecraft.getInstance().getModelManager().getModel(ModelResourceLocation.standalone(model));
    }

    /** 解析物品及其世界、持有者驱动的模型覆盖。世界和持有者可以为空。 */
    public static BakedModel resolveItem(ItemStack stack, @Nullable Level level, @Nullable LivingEntity owner, int seed) {
        return Minecraft.getInstance().getItemRenderer().getModel(stack, level, owner, seed);
    }

    /** 以指定姿态、亮度和覆盖层向渲染缓冲区绘制方块模型。 */
    public static void renderBlock(BlockState block, PoseStack poses, MultiBufferSource output, int light, int overlay) {
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(block, poses, output, light, overlay);
    }

    /** 采集一次加权模型选择的全部通用面和各方向剔除面。 */
    public static ObjectList<QuadUtils.Face> quads(BakedModel model, RandomSource random) {
        return quads(model, null, random, ModelData.EMPTY);
    }

    /** 使用当前方块视图读取模型。连接纹理等上下文相关模型从视图获取数据。 */
    public static ObjectList<QuadUtils.Face> quads(BakedModel model, BlockAndTintGetter level, BlockPos pos,
                                                   BlockState state, RandomSource random) {
        return quads(model, state, random, model.getModelData(level, pos, state, ModelData.EMPTY));
    }

    /** 收集模型的通用面及六个方向的剔除分组，各方向使用同一个变体种子。 */
    public static ObjectList<QuadUtils.Face> quads(BakedModel model, @Nullable BlockState state, RandomSource random,
                                                   ModelData data) {
        var result = new ObjectArrayList<QuadUtils.Face>();
        long seed = random.nextLong();
        RandomSource sample = RandomSource.create(seed);
        for (BakedQuad quad : model.getQuads(state, null, sample, data, null)) result.add(new QuadUtils.Face(quad, null));
        for (Direction direction : DIRECTIONS) {
            sample.setSeed(seed);
            for (BakedQuad quad : model.getQuads(state, direction, sample, data, null)) result.add(new QuadUtils.Face(quad, direction));
        }
        return new ObjectImmutableList<>(result);
    }

    /** 给由方块实体渲染器绘制几何的方块提供粒子贴图。 */
    public static BakedModel particleOnly(TextureAtlasSprite sprite) {
        var culled = new EnumMap<Direction, List<BakedQuad>>(Direction.class);
        for (Direction direction : DIRECTIONS) culled.put(direction, List.of());
        return new SimpleBakedModel(List.of(), culled, false, false, false, sprite,
                ItemTransforms.NO_TRANSFORMS, ItemOverrides.EMPTY, RenderTypeGroup.EMPTY);
    }

    /** 按输入顺序组合已烘焙模型。各部分共享随机变体种子，粒子贴图取第一项。 */
    public static BakedModel combine(List<? extends BakedModel> parts) {
        if (parts.isEmpty()) throw new IllegalArgumentException("模型组合需要至少一个部件");
        if (parts.size() == 1) return parts.getFirst();
        return new CombinedModel(List.copyOf(parts));
    }

    private static final class CombinedModel extends BakedModelWrapper<BakedModel> {

        private final ModelProperty<List<ModelData>> partData = new ModelProperty<>();
        private final List<BakedModel> parts;

        private CombinedModel(List<BakedModel> parts) {
            super(parts.getFirst());
            this.parts = parts;
        }

        @Override
        @Deprecated
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random) {
            return getQuads(state, direction, random, ModelData.EMPTY, null);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random,
                                        ModelData data, @Nullable RenderType layer) {
            var output = new ObjectArrayList<BakedQuad>();
            long seed = random.nextLong();
            RandomSource sample = RandomSource.create(seed);
            var dataByPart = data.get(partData);
            for (int index = 0; index < parts.size(); index++) {
                BakedModel part = parts.get(index);
                ModelData modelData = dataByPart == null ? data : dataByPart.get(index);
                if (layer != null && state != null && !part.getRenderTypes(state, sample, modelData).contains(layer)) {
                    sample.setSeed(seed);
                    continue;
                }
                sample.setSeed(seed);
                output.addAll(part.getQuads(state, direction, sample, modelData, layer));
                sample.setSeed(seed);
            }
            return output;
        }

        @Override
        public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
            var byPart = new ObjectArrayList<ModelData>(parts.size());
            for (BakedModel part : parts) byPart.add(part.getModelData(level, pos, state, data));
            return byPart.getFirst().derive().with(partData, byPart).build();
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            long seed = random.nextLong();
            RandomSource sample = RandomSource.create(seed);
            var types = new ObjectArrayList<ChunkRenderTypeSet>(parts.size());
            var dataByPart = data.get(partData);
            for (int index = 0; index < parts.size(); index++) {
                sample.setSeed(seed);
                types.add(parts.get(index).getRenderTypes(state, sample, dataByPart == null ? data : dataByPart.get(index)));
            }
            return ChunkRenderTypeSet.union(types);
        }
    }
}
