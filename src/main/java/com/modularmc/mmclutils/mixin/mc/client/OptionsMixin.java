package com.modularmc.mmclutils.mixin.mc.client;

import com.modularmc.mmclutils.client.input.KeyChordMapping;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Options.class)
public abstract class OptionsMixin {

    @Shadow
    public KeyMapping[] keyMappings;

    @Inject(method = "processOptions", at = @At("TAIL"))
    private void mmclutils$processChords(Options.FieldAccess access, CallbackInfo callback) {
        for (KeyMapping mapping : keyMappings) {
            if (mapping instanceof KeyChordMapping chord) chord.processOptions(access);
        }
    }
}
