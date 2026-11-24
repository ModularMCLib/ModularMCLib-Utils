package com.modularmc.mmclutils.mixin.mc.client;

import com.modularmc.mmclutils.client.input.ClientInput;
import com.modularmc.mmclutils.client.input.KeyChordMapping;

import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;

import com.mojang.blaze3d.platform.InputConstants;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 将自定义组合键的录入交给客户端绑定模型解析与保存。 */
@Mixin(KeyBindsScreen.class)
public abstract class KeyBindsScreenMixin {

    @Shadow
    public @Nullable KeyMapping selectedKey;
    @Shadow
    public long lastKeySelection;
    @Shadow
    private KeyBindsList keyBindsList;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void mmclutils$captureKey(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> callback) {
        if (!(selectedKey instanceof KeyChordMapping chord)) return;
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) mmclutils$finish(chord, InputConstants.UNKNOWN, 0);
        else if (ClientInput.keyModifierBit(key) == 0) mmclutils$finish(chord, key, ClientInput.modifiers());
        callback.setReturnValue(true);
    }

    @Inject(method = "keyReleased", at = @At("HEAD"), cancellable = true)
    private void mmclutils$releaseKey(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> callback) {
        if (!(selectedKey instanceof KeyChordMapping chord)) return;
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);
        if (ClientInput.keyModifierBit(key) != 0) mmclutils$finish(chord, key, ClientInput.modifiers());
        callback.setReturnValue(true);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void mmclutils$captureMouse(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> callback) {
        if (!(selectedKey instanceof KeyChordMapping chord)) return;
        mmclutils$finish(chord, InputConstants.Type.MOUSE.getOrCreate(button), ClientInput.modifiers());
        callback.setReturnValue(true);
    }

    @Unique
    private void mmclutils$finish(KeyChordMapping chord, InputConstants.Key key, int modifiers) {
        chord.setChord(key, modifiers);
        selectedKey = null;
        lastKeySelection = Util.getMillis();
        keyBindsList.resetMappingAndUpdateButtons();
    }
}
