package com.modularmc.mmclutils.client.input;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.InputQuirks;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** 在客户端线程读取键盘和鼠标的当前状态。 */
public final class ClientInput {

    public static final int CONTROL = 1;
    public static final int SHIFT = 2;
    public static final int ALT = 4;
    public static final int COMMAND = 8;

    private ClientInput() {}

    public static int modifiers() {
        return (KeyModifier.CONTROL.isActive(KeyConflictContext.UNIVERSAL) ? CONTROL : 0) | (KeyModifier.SHIFT.isActive(KeyConflictContext.UNIVERSAL) ? SHIFT : 0) | (KeyModifier.ALT.isActive(KeyConflictContext.UNIVERSAL) ? ALT : 0) | (InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY && KeyModifier.CONTROL_OR_COMMAND.isActive(KeyConflictContext.UNIVERSAL) ? COMMAND : 0);
    }

    public static int modifierBit(KeyModifier modifier) {
        return switch (modifier) {
            case CONTROL -> CONTROL;
            case CONTROL_OR_COMMAND -> InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY ? COMMAND : CONTROL;
            case SHIFT -> SHIFT;
            case ALT -> ALT;
            case NONE -> 0;
        };
    }

    /** 直接读取主键。扫描码使用原版记录的状态，当前输入事件可以提供更早的状态变化。 */
    public static boolean mainKeyDown(Window window, KeyMapping mapping, InputConstants.@Nullable Key changed, boolean down) {
        if (mapping.isUnbound()) return false;
        InputConstants.Key key = mapping.getKey();
        if (key.equals(changed)) return down;
        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window.handle(), key.getValue()) == GLFW.GLFW_PRESS;
            case SCANCODE -> mapping.isDown();
        };
    }
}
