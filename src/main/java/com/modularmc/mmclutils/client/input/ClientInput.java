package com.modularmc.mmclutils.client.input;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** 在客户端线程读取键盘和鼠标的当前状态。 */
public final class ClientInput {

    /** 表示物理 Ctrl 修饰键的位。 */
    public static final int CONTROL = 1;
    /** 表示 Shift 修饰键的位。 */
    public static final int SHIFT = 2;
    /** 表示 Alt 或 Option 修饰键的位。 */
    public static final int ALT = 4;
    /** 表示使用 Command 的平台上的命令修饰位。 */
    public static final int COMMAND = 8;
    /** 本库识别的全部修饰位。 */
    public static final int ALL_MODIFIERS = CONTROL | SHIFT | ALT | COMMAND;

    private ClientInput() {}

    /** 读取当前按下的全部修饰键并组成位掩码。请在客户端线程调用。 */
    public static int modifiers() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean control = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) ||
                InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean command = Minecraft.ON_OSX && KeyModifier.CONTROL.isActive(KeyConflictContext.UNIVERSAL);
        return (control ? CONTROL : 0) | (command ? COMMAND : 0) |
                (KeyModifier.SHIFT.isActive(KeyConflictContext.UNIVERSAL) ? SHIFT : 0) |
                (KeyModifier.ALT.isActive(KeyConflictContext.UNIVERSAL) ? ALT : 0);
    }

    /** 将 NeoForge 修饰键转换成当前平台的掩码位。无修饰键则返回零。 */
    public static int modifierBit(KeyModifier modifier) {
        return switch (modifier) {
            case CONTROL -> Minecraft.ON_OSX ? COMMAND : CONTROL;
            case SHIFT -> SHIFT;
            case ALT -> ALT;
            case NONE -> 0;
        };
    }

    /** 把输入键转换成对应修饰位。普通输入键或不受支持的 Command 键返回零。 */
    public static int keyModifierBit(InputConstants.Key key) {
        if (key.getType() != InputConstants.Type.KEYSYM) return 0;
        return switch (key.getValue()) {
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> CONTROL;
            case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> Minecraft.ON_OSX ? COMMAND : 0;
            default -> modifierBit(KeyModifier.getKeyModifier(key));
        };
    }

    /** 直接读取主键。扫描码使用原版记录的状态，当前输入事件可以提供更早的状态变化。 */
    public static boolean mainKeyDown(Window window, KeyMapping mapping, InputConstants.@Nullable Key changed, boolean down) {
        if (mapping.isUnbound()) return false;
        InputConstants.Key key = mapping.getKey();
        if (key.equals(changed)) return down;
        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window.getWindow(), key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window.getWindow(), key.getValue()) == GLFW.GLFW_PRESS;
            case SCANCODE -> mapping.isDown();
        };
    }
}
