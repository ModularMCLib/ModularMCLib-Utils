package com.modularmc.mmclutils.mixin.mc.client;

import com.mojang.blaze3d.opengl.GlBuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GlBuffer.class)
public interface GlBufferAccessor {

    @Accessor("handle")
    int mmclutils$handle();
}
