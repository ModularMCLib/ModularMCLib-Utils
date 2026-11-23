package com.modularmc.mmclutils.mixin.client.level;

import com.modularmc.mmclutils.client.level.ClientChunkVisibility;
import com.modularmc.mmclutils.client.level.ClientChunkVisibility.View;

import net.minecraft.client.multiplayer.ClientChunkCache;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {

    @Shadow
    private volatile ClientChunkCache.Storage storage;

    @Unique
    private ClientChunkCache.Storage mmclutils$previousStorage;

    @Inject(method = "updateViewCenter", at = @At("TAIL"))
    private void mmclutils$afterCenter(int x, int z, CallbackInfo ci) {
        ClientChunkVisibility.sync((View) (Object) storage);
    }

    @Inject(method = "updateViewRadius", at = @At("HEAD"))
    private void mmclutils$beforeRadius(int viewDistance, CallbackInfo ci) {
        mmclutils$previousStorage = storage;
    }

    @Inject(method = "updateViewRadius", at = @At("TAIL"))
    private void mmclutils$afterRadius(int viewDistance, CallbackInfo ci) {
        ClientChunkCache.Storage previous = mmclutils$previousStorage;
        mmclutils$previousStorage = null;
        ClientChunkVisibility.afterResize((View) (Object) storage, (View) (Object) previous);
    }
}
