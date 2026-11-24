package com.modularmc.mmclutils.mixin.mc.client;

import com.mojang.blaze3d.opengl.GlBuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 为渲染后端读取原版 GL 缓冲对象的驱动句柄。 */
@Mixin(GlBuffer.class)
public interface GlBufferAccessor {

    /** 返回原版缓冲包装所持有的 OpenGL 缓冲标识。 */
    @Accessor("handle")
    int mmclutils$handle();
}
