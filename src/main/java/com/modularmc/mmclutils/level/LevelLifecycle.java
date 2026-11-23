package com.modularmc.mmclutils.level;

import com.modularmc.mmclutils.ModularMCLibUtils;

import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import org.jetbrains.annotations.ApiStatus;

/** 维度卸载时释放挂在实例上的状态和调度资源。 */
@ApiStatus.Internal
@EventBusSubscriber(modid = ModularMCLibUtils.MOD_ID)
public final class LevelLifecycle {

    private LevelLifecycle() {}

    @SubscribeEvent
    private static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) LevelStore.releaseAll(level);
    }
}
