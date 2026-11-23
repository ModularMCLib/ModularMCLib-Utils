package com.modularmc.mmclutils.item;

import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 将原版有序材料表转换为带稳定字符键的配方图案。 */
public final class ShapedRecipePatterns {

    private ShapedRecipePatterns() {}

    public static Pattern of(List<Optional<Ingredient>> ingredients, int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height != ingredients.size()) {
            throw new IllegalArgumentException("配方尺寸与材料数量不匹配");
        }
        Map<Ingredient, Character> keys = new LinkedHashMap<>();
        List<String> rows = new ArrayList<>(height);
        for (int y = 0; y < height; y++) {
            var line = new StringBuilder(width);
            for (int x = 0; x < width; x++) {
                var input = ingredients.get(y * width + x);
                line.append(input.isEmpty() ? ' ' : keys.computeIfAbsent(input.orElseThrow(), ignored -> (char) ('A' + keys.size())));
            }
            rows.add(line.toString());
        }
        Map<Character, Ingredient> keyMap = new LinkedHashMap<>();
        keys.forEach((ingredient, key) -> keyMap.put(key, ingredient));
        return new Pattern(List.copyOf(rows), Map.copyOf(keyMap));
    }

    public record Pattern(List<String> rows, Map<Character, Ingredient> keys) {}
}
