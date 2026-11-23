package com.modularmc.mmclutils.registry;

import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.Arrays;
import java.util.List;

public class RegistryEntries {

    private RegistryEntries() {}

    public static <T> List<ResourceKey<T>> resolveResourceKeys(ResourceKey<Registry<T>> registryKey,
                                                               String... locations) {
        return Arrays.stream(locations)
                .map(location -> ResourceKey.create(registryKey, Identifier.parse(location)))
                .toList();
    }
}
