package com.modularmc.mmclutils.api.recipe;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

import it.unimi.dsi.fastutil.chars.Char2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2CharOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;

/** 将原版有序材料表转换为带稳定字符键的配方图案。 */
public final class ShapedRecipePatterns {

    private ShapedRecipePatterns() {}

    /**
     * 将原版有序配方输入转换为稳定字符表和行。
     *
     * @param pattern 原版有序配方图案
     * @return 按材料首次出现顺序分配字符的图案
     */
    public static Pattern of(ShapedRecipePattern pattern) {
        return of(pattern.ingredients(), pattern.width(), pattern.height());
    }

    /**
     * 按材料首次出现顺序从 A 开始分配字符并构建配方行。
     *
     * @param ingredients 按行排列的可选材料
     * @param width       配方宽度
     * @param height      配方高度
     * @return 稳定的配方行与只读字符表
     * @throws IllegalArgumentException 配方尺寸无效，或不同材料多于可用字符数
     */
    public static Pattern of(List<Optional<Ingredient>> ingredients, int width, int height) {
        var symbols = new Object2CharOpenHashMap<Ingredient>();
        var keys = new Char2ObjectLinkedOpenHashMap<Ingredient>();
        ToIntFunction<Ingredient> nextSymbol = ingredient -> {
            int symbol = 'A' + keys.size();
            if (symbol > Character.MAX_VALUE) throw new IllegalArgumentException("配方中的不同材料超出可用字符数量");
            keys.put((char) symbol, ingredient);
            return symbol;
        };
        List<String> rows = buildRows(ingredients, width, height, ingredient -> symbols.computeIfAbsent(ingredient, nextSymbol));
        return new Pattern(rows, Char2ObjectMaps.unmodifiable(keys));
    }

    /**
     * 用已有字符表重建配方行。同一材料对应多个字符时使用映射迭代顺序中最先出现的字符。
     *
     * @param ingredients 按行排列的可选材料
     * @param width       配方宽度
     * @param height      配方高度
     * @param keys        字符到材料的映射，空格字符保留给空槽
     * @return 按配方行顺序生成的字符串列表
     * @throws IllegalArgumentException 配方尺寸无效、字符表包含空格键或缺少材料映射
     */
    public static List<String> rows(List<Optional<Ingredient>> ingredients, int width, int height, Map<Character, Ingredient> keys) {
        var symbols = new Object2CharOpenHashMap<Ingredient>(keys.size());
        symbols.defaultReturnValue(' ');
        keys.forEach((symbol, ingredient) -> {
            if (symbol == ' ') throw new IllegalArgumentException("空格保留给配方图案中的空槽");
            symbols.putIfAbsent(ingredient, symbol.charValue());
        });
        return buildRows(ingredients, width, height, ingredient -> {
            char symbol = symbols.getChar(ingredient);
            if (symbol == ' ') throw new IllegalArgumentException("配方字符表缺少材料 " + ingredient);
            return symbol;
        });
    }

    private static List<String> buildRows(List<Optional<Ingredient>> ingredients, int width, int height, ToIntFunction<Ingredient> symbol) {
        if (width <= 0 || height <= 0 || (long) width * height != ingredients.size()) {
            throw new IllegalArgumentException("配方尺寸与材料数量不匹配");
        }
        var inputs = ingredients.iterator();
        List<String> rows = new ObjectArrayList<>(height);
        for (int y = 0; y < height; y++) {
            var line = new StringBuilder(width);
            for (int x = 0; x < width; x++) {
                var input = inputs.next();
                line.append(input.isEmpty() ? ' ' : (char) symbol.applyAsInt(input.orElseThrow()));
            }
            rows.add(line.toString());
        }
        return List.copyOf(rows);
    }

    /**
     * 有序配方行及其字符到材料的映射。
     *
     * @param rows 每行一个字符串，空槽以空格表示
     * @param keys 字符到非空材料的映射
     */
    public record Pattern(List<String> rows, Map<Character, Ingredient> keys) {}
}
