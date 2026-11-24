package com.modularmc.mmclutils.mixin.level;

import com.modularmc.mmclutils.api.data.FastSavedData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelSaveMixin {

    @Inject(method = "save", at = @At("RETURN"))
    private void mmclutils$finishSave(@Nullable ProgressListener progress, boolean flush, boolean noSave, CallbackInfo callback) {
        if (flush && !noSave) FastSavedData.flush((ServerLevel) (Object) this);
    }
}
