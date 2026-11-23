package com.modularmc.mmclutils;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ModularMCLibUtils.MOD_ID)
public class ModularMCLibUtils {

    public static final String MOD_ID = "mmclutils";
    public static final String MOD_NAME = "ModularMCLib Utils";
    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);

    public ModularMCLibUtils(IEventBus bus) {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
