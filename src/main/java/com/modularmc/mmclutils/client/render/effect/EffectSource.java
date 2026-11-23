package com.modularmc.mmclutils.client.render.effect;

import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** 在世界渲染状态提取时提交效果，回调在客户端渲染线程执行。 */
@OnlyIn(Dist.CLIENT)
@FunctionalInterface
public interface EffectSource {

    /** 使用当前世界、动画时间和相机坐标生成本帧指令。回调返回后本帧收集结束。 */
    void collect(ClientLevel level, double time, double camX, double camY, double camZ);
}
