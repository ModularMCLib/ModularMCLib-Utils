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
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/** 可在控制设置中编辑和保存的组合键。只在客户端线程读写。 */
public final class KeyChordMapping extends KeyMapping {

    private static final KeyModifier[] DISPLAY_ORDER = { KeyModifier.ALT, KeyModifier.SHIFT, KeyModifier.CONTROL,
            KeyModifier.CONTROL_OR_COMMAND };
    private final int defaultModifiers;
    /** 当前组合要求的完整修饰键掩码。 */
    @Getter
    @Accessors(fluent = true)
    private int modifiers;
    private @Nullable LegacyOptions legacyOptions;

    /** 用主键和修饰掩码创建绑定，自动去掉掩码中与主键重复的修饰位。 */
    public KeyChordMapping(String name, Category category, IKeyConflictContext context, InputConstants.Key key, int modifiers) {
        super(name, context, primary(normalize(key, modifiers)), key, category);
        defaultModifiers = normalize(key, modifiers);
        this.modifiers = defaultModifiers;
    }

    /** 用输入类型和按键编号创建组合。编号的解释遵循对应的原版输入类型。 */
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

    /** 从此库此前使用的按键名称读取设置。在绑定注册前指定旧名称。 */
    public KeyChordMapping migrateFrom(String previousName) {
        return migrateFrom(previousName, "mmclutils_chord_" + previousName);
    }

    /**
     * 在注册前指定旧设置的位置。当前设置缺失时才尝试读取对应旧值。
     *
     * @param previousName     旧按键名称，省略原版的 {@code key_} 前缀
     * @param previousChordKey 旧附加选项的完整键名，值为主键、主修饰键和位掩码以冒号分隔的格式。空值代表没有附加选项
     * @return 当前组合键
     */
    public KeyChordMapping migrateFrom(String previousName, @Nullable String previousChordKey) {
        legacyOptions = new LegacyOptions("key_" + previousName, previousChordKey);
        return this;
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
    @ApiStatus.Internal
    public void processOptions(Options.FieldAccess access) {
        readModifiers(access.process(chordOption(), optionPrefix() + modifiers));
    }

    /** 在原版读取完成后补入仍缺失的配置。只在载入选项时调用。 */
    @ApiStatus.Internal
    public void readLegacyOptions(Options.FieldAccess access) {
        LegacyOptions legacy = legacyOptions;
        if (legacy == null) return;
        if (access.process("key_" + getName(), "").isEmpty()) {
            String saved = access.process(legacy.mappingKey(), "");
            if (!saved.isEmpty()) {
                try {
                    String[] parts = saved.split(":", 2);
                    setKeyModifierAndCode(parts.length == 2 ? KeyModifier.valueFromString(parts[1]) : KeyModifier.NONE,
                            InputConstants.getKey(parts[0]));
                } catch (IllegalArgumentException failure) {
                    ModularMCLibUtils.LOGGER.warn("组合键 {} 的旧主键设置无效，旧设置键为 {}", getName(), legacy.mappingKey(), failure);
                }
            }
        }
        if (legacy.chordKey() != null && access.process(chordOption(), "").isEmpty()) {
            readModifiers(access.process(legacy.chordKey(), ""));
        }
    }

    private String chordOption() {
        return "mmclutils_chord_" + getName();
    }

    private String optionPrefix() {
        return getKey().getName() + ":" + getKeyModifier().name() + ":";
    }

    private void readModifiers(String saved) {
        String prefix = optionPrefix();
        if (!saved.startsWith(prefix) || saved.equals(prefix + modifiers)) return;
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

    private record LegacyOptions(String mappingKey, @Nullable String chordKey) {}
}
