package com.modularmc.mmclutils.client;

import com.modularmc.mmclutils.ModularMCLibUtils;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** 注册仅在物理客户端加载的入口。 */
@Mod(value = ModularMCLibUtils.MOD_ID, dist = Dist.CLIENT)
public final class ModularMCLibUtilsClient {

    public ModularMCLibUtilsClient(IEventBus modBus) {}
}
