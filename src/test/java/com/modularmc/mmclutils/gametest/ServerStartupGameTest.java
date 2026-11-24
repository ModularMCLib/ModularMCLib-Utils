package com.modularmc.mmclutils.gametest;

import com.modularmc.mmclutils.ModularMCLibUtils;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModularMCLibUtils.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ServerStartupGameTest {

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void serverReachesGameTest(GameTestHelper helper) {
        helper.succeed();
    }
}
