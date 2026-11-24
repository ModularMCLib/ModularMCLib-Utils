package com.modularmc.mmclutils.client.input;

import com.modularmc.mmclutils.ModularMCLibUtils;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

import com.mojang.blaze3d.platform.InputConstants;
import lombok.Getter;
import lombok.experimental.Accessors;

/** 可在控制设置中编辑和保存的组合键。只在客户端线程读写。 */
public final class KeyChordMapping extends KeyMapping {

    private static final KeyModifier[] DISPLAY_ORDER = { KeyModifier.ALT, KeyModifier.SHIFT, KeyModifier.CONTROL,
            KeyModifier.CONTROL_OR_COMMAND };
    private final int defaultModifiers;
    @Getter
    @Accessors(fluent = true)
    private int modifiers;

    public KeyChordMapping(String name, Category category, IKeyConflictContext context, InputConstants.Key key, int modifiers) {
        super(name, context, primary(normalize(key, modifiers)), key, category);
        defaultModifiers = normalize(key, modifiers);
        this.modifiers = defaultModifiers;
    }

    public KeyChordMapping(String name, Category category, IKeyConflictContext context, InputConstants.Type type, int key, int modifiers) {
        this(name, category, context, type.getOrCreate(key), modifiers);
    }

    /** 更换主键及完整修饰键。清除旧绑定积累的点击。 */
    public void setChord(InputConstants.Key key, int modifiers) {
        this.modifiers = normalize(key, modifiers);
        super.setKeyModifierAndCode(primary(this.modifiers), key);
        setDown(false);
        while (consumeClick()) {
            // 旧绑定的点击不能触发新的组合键。
        }
    }

    @Override
    public void setKeyModifierAndCode(KeyModifier modifier, InputConstants.Key key) {
        setChord(key, ClientInput.modifierBit(modifier));
    }

    @Override
    public boolean isConflictContextAndModifierActive() {
        if (!getKeyConflictContext().isActive()) return false;
        return modifiers == 0 ? super.isConflictContextAndModifierActive() : normalize(getKey(), ClientInput.modifiers()) == modifiers;
    }

    @Override
    public boolean isDefault() {
        return getKey().equals(getDefaultKey()) && modifiers == defaultModifiers;
    }

    @Override
    public void setToDefault() {
        setChord(getDefaultKey(), defaultModifiers);
    }

    @Override
    public Component getTranslatedKeyMessage() {
        Component result = getKey().getDisplayName();
        for (KeyModifier modifier : DISPLAY_ORDER) {
            if (modifier == KeyModifier.CONTROL_OR_COMMAND && ClientInput.modifierBit(modifier) != ClientInput.COMMAND) continue;
            if ((modifiers & ClientInput.modifierBit(modifier)) == 0) continue;
            Component suffix = result;
            result = modifier.getCombinedName(getKey(), () -> suffix);
        }
        return result;
    }

    @Override
    public boolean same(KeyMapping other) {
        if (!conflicts(other)) return false;
        if (hasKeyModifierConflict(other) || other.hasKeyModifierConflict(this)) return true;
        if (isUnbound() || !getKey().equals(other.getKey())) return false;
        int otherModifiers = other instanceof KeyChordMapping chord ? chord.modifiers : ClientInput.modifierBit(other.getKeyModifier());
        return modifiers == otherModifiers || (modifiers == 0 || otherModifiers == 0) && (getKeyConflictContext().conflicts(KeyConflictContext.IN_GAME) || other.getKeyConflictContext().conflicts(KeyConflictContext.IN_GAME));
    }

    @Override
    public boolean hasKeyModifierConflict(KeyMapping other) {
        return conflicts(other) && (modifiers & ClientInput.keyModifierBit(other.getKey())) != 0;
    }

    /** 只恢复与已加载的主键和 NeoForge 主修饰键匹配的组合。 */
    public void processOptions(Options.FieldAccess access) {
        String prefix = getKey().getName() + ":" + getKeyModifier().name() + ":";
        String current = prefix + modifiers;
        String saved = access.process("mmclutils_chord_" + getName(), current);
        if (!saved.startsWith(prefix) || saved.equals(current)) return;
        try {
            setChord(getKey(), Integer.parseInt(saved.substring(prefix.length())));
        } catch (IllegalArgumentException failure) {
            ModularMCLibUtils.LOGGER.warn("组合键 {} 的附加配置无效，使用已读取的主键和修饰键", getName(), failure);
        }
    }

    private boolean conflicts(KeyMapping other) {
        return getKeyConflictContext().conflicts(other.getKeyConflictContext()) || other.getKeyConflictContext().conflicts(getKeyConflictContext());
    }

    private static int normalize(InputConstants.Key key, int modifiers) {
        if ((modifiers & ~ClientInput.ALL_MODIFIERS) != 0) throw new IllegalArgumentException("未知修饰键 " + modifiers);
        return key.equals(InputConstants.UNKNOWN) ? 0 : modifiers & ~ClientInput.keyModifierBit(key);
    }

    private static KeyModifier primary(int modifiers) {
        if ((modifiers & ClientInput.CONTROL) != 0) return KeyModifier.CONTROL;
        if ((modifiers & ClientInput.COMMAND) != 0) return KeyModifier.CONTROL_OR_COMMAND;
        if ((modifiers & ClientInput.SHIFT) != 0) return KeyModifier.SHIFT;
        return (modifiers & ClientInput.ALT) != 0 ? KeyModifier.ALT : KeyModifier.NONE;
    }
}
