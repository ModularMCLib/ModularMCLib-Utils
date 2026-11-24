package com.modularmc.mmclutils.mixin.mc.client;

import com.modularmc.mmclutils.client.input.KeyChordMapping;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在选项序列化流程中读写组合键的主键与修饰键。 */
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

    @WrapOperation(method = "load", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;processOptions(Lnet/minecraft/client/Options$FieldAccess;)V"))
    private void mmclutils$readLegacyChords(Options options, Options.FieldAccess access, Operation<Void> original) {
        original.call(options, access);
        for (KeyMapping mapping : keyMappings) {
            if (mapping instanceof KeyChordMapping chord) chord.readLegacyOptions(access);
        }
    }
}
