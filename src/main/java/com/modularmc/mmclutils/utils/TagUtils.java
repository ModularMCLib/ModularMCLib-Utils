package com.modularmc.mmclutils.utils;

import com.modularmc.mmclutils.utils.FileUtils.Serializer;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader.EntryWithSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 构造原版标签键，读写旧标签条目并解析物品和流体的标签匹配表达式。 */
public final class TagUtils {

    /**
     * 旧标签条目的二进制格式。先写命名空间和路径，再按顺序写入标签引用、必需和移除三个布尔值。
     * 此格式没有来源包名称字段，读取时将来源设为空字符串。
     */
    public static final Serializer<EntryWithSource> ENTRY_WITH_SOURCE_SERIALIZER = new Serializer<>(input -> {
        Identifier id = Serializer.IDENTIFIER_PARTS.read(input);
        boolean tag = input.readBoolean();
        boolean required = input.readBoolean();
        TagEntry entry;
        if (tag) {
            entry = required ? TagEntry.tag(id) : TagEntry.optionalTag(id);
        } else {
            entry = required ? TagEntry.element(id) : TagEntry.optionalElement(id);
        }
        return new EntryWithSource(entry, "", input.readBoolean());
    }, (output, value) -> {
        TagEntry entry = value.entry();
        Serializer.IDENTIFIER_PARTS.write(output, entry.getId());
        output.writeBoolean(entry.isTag());
        output.writeBoolean(entry.isRequired());
        output.writeBoolean(value.remove());
    });

    private TagUtils() {}

    /** 构造指定标识的方块标签键，不查询注册表。 */
    public static TagKey<Block> block(Identifier id) {
        return TagKey.create(Registries.BLOCK, id);
    }

    /** 构造指定标识的物品标签键，不查询注册表。 */
    public static TagKey<Item> item(Identifier id) {
        return TagKey.create(Registries.ITEM, id);
    }

    /** 构造指定标识的流体标签键，不查询注册表。 */
    public static TagKey<Fluid> fluid(Identifier id) {
        return TagKey.create(Registries.FLUID, id);
    }

    /**
     * 将标签条件解析成可反复匹配的表达式。支持括号、前缀取反及从左至右的二元逻辑运算。
     * 每个实例在同一线程使用，已返回的表达式可跨线程匹配。
     */
    public static class TagExprParser {

        /** 标签文本、括号与逻辑运算符的记号类型。 */
        public enum TokenType {
            /** 左括号。 */
            LParen,
            /** 右括号。 */
            RParen,
            /** 逻辑与。 */
            And,
            /** 逻辑或。 */
            Or,
            /** 前缀取反。 */
            Not,
            /** 逻辑异或。 */
            Xor,
            /** 标签文本。 */
            String
        }

        /** 分词得到的类型与原始文本。 */
        public static class Token {

            /** 标签的原始文本，运算符与括号对应空值。 */
            @Nullable
            public String lexeme;
            /** 该记号的语法类型。 */
            public TokenType type;

            /** 创建没有文本的运算符或括号记号。 */
            public Token(TokenType type) {
                this.type = type;
            }

            /** 创建带原始文本的记号。 */
            public Token(TokenType type, String lexeme) {
                this.lexeme = lexeme;
                this.type = type;
            }
        }

        /** 已经解析的标签条件，供不同物品和流体反复匹配。 */
        public static abstract class MatchExpr {

            /**
             * 计算给定标签集合是否满足表达式。
             *
             * @param input 标签的完整标识集合，可以为空集合
             * @return 是否匹配
             */
            public abstract boolean matches(Set<String> input);
        }

        private static final class BinExpr extends MatchExpr {

            private final MatchExpr left, right;
            private final TokenType op;

            private BinExpr(TokenType op, MatchExpr left, MatchExpr right) {
                this.op = op;
                this.left = left;
                this.right = right;
            }

            @Override
            public boolean matches(Set<String> input) {
                return switch (op) {
                    case And -> left.matches(input) && right.matches(input);
                    case Or -> left.matches(input) || right.matches(input);
                    case Xor -> left.matches(input) ^ right.matches(input);
                    default -> throw new IllegalStateException("无效的二元运算符 " + op);
                };
            }
        }

        private static final class UnaryExpr extends MatchExpr {

            private final MatchExpr expr;

            private UnaryExpr(MatchExpr expr) {
                this.expr = expr;
            }

            @Override
            public boolean matches(Set<String> input) {
                return !expr.matches(input);
            }
        }

        private static final class StringExpr extends MatchExpr {

            private final @Nullable Pattern pattern;

            private StringExpr(String value) {
                if (value.equals("$")) {
                    pattern = null;
                    return;
                }
                String tag = value.contains(":") || value.startsWith("*") ? value : "c:" + value;
                StringBuilder regex = new StringBuilder();
                int start = 0;
                for (int star = tag.indexOf('*'); star >= 0; star = tag.indexOf('*', start)) {
                    regex.append(Pattern.quote(tag.substring(start, star))).append(".*");
                    start = star + 1;
                }
                pattern = Pattern.compile(regex.append(Pattern.quote(tag.substring(start))).toString());
            }

            @Override
            public boolean matches(Set<String> input) {
                if (pattern == null) return input.isEmpty();
                for (String tag : input) {
                    if (pattern.matcher(tag).matches()) return true;
                }
                return false;
            }
        }

        private List<Token> tokens = List.of();
        private int idx;

        /**
         * 编译一个完整表达式。星号匹配任意字符，单独的美元符号匹配没有标签的对象。
         * 未指定命名空间且没有首位通配符的标签默认为 {@code c}。
         *
         * @param expr 表达式文本
         * @return 可复用条件，空表达式或语法不完整时返回空值
         */
        @Nullable
        public MatchExpr parse(String expr) {
            tokens = tokenize(expr);
            idx = 0;
            try {
                MatchExpr result = term();
                return idx == tokens.size() ? result : null;
            } finally {
                tokens = List.of();
            }
        }

        private boolean match(TokenType tt) {
            if (idx >= tokens.size()) {
                return false;
            }

            if (tokens.get(idx).type == tt) {
                idx++;
                return true;
            }

            return false;
        }

        @Nullable
        private MatchExpr term() {
            MatchExpr lhs = unary();
            if (lhs == null) return null;
            while (match(TokenType.And) || match(TokenType.Or) || match(TokenType.Xor)) {
                TokenType op = tokens.get(idx - 1).type;
                MatchExpr rhs = unary();
                if (rhs == null) return null;
                lhs = new BinExpr(op, lhs, rhs);
            }

            return lhs;
        }

        @Nullable
        private MatchExpr unary() {
            boolean negate = false;
            while (match(TokenType.Not)) negate = !negate;
            MatchExpr expr = id();
            return negate && expr != null ? new UnaryExpr(expr) : expr;
        }

        @Nullable
        private MatchExpr id() {
            if (match(TokenType.LParen)) {
                MatchExpr inner = term();
                return match(TokenType.RParen) ? inner : null;
            }

            if (match(TokenType.String)) {
                String lexeme = tokens.get(idx - 1).lexeme;
                assert lexeme != null;
                return new StringExpr(lexeme);
            }

            return null;
        }

        private List<Token> tokenize(String expr) {
            List<Token> result = new ObjectArrayList<>();

            int idx = 0;
            while (idx < expr.length()) {
                char cur = expr.charAt(idx);

                if (Character.isWhitespace(cur)) {
                    idx++;
                    continue;
                }

                // 读取标签文本。
                {
                    int stringLen = 0;
                    while (cur != '(' && cur != ')' && cur != '!' && cur != '&' && cur != '|' && cur != '^' &&
                            !Character.isWhitespace(cur)) {
                        stringLen++;

                        if (stringLen + idx == expr.length()) {
                            break;
                        }

                        cur = expr.charAt(idx + stringLen);
                    }

                    if (stringLen > 0) {
                        result.add(new Token(TokenType.String, expr.substring(idx, idx + stringLen)));
                        idx += stringLen;
                        continue;
                    }
                }

                // 读取运算符。
                switch (cur) {
                    case '!' -> result.add(new Token(TokenType.Not));
                    case '&' -> result.add(new Token(TokenType.And));
                    case '|' -> result.add(new Token(TokenType.Or));
                    case '^' -> result.add(new Token(TokenType.Xor));
                    case '(' -> result.add(new Token(TokenType.LParen));
                    case ')' -> result.add(new Token(TokenType.RParen));
                }

                idx++;
            }

            return result;
        }
    }

    /**
     * 将标签表达式解析为匹配节点。
     *
     * @param expression 待解析的表达式
     * @return 可复用的匹配节点，空表达式和无效语法返回空值
     */
    public static TagExprParser.@Nullable MatchExpr parseExpression(String expression) {
        return new TagExprParser().parse(expression);
    }

    /**
     * 判断物品是否满足标签表达式。
     *
     * @param expr  匹配表达式，空值表示不匹配
     * @param stack 待检查物品栈
     * @return 物品的标签是否符合表达式
     */
    public static boolean tagsMatch(TagExprParser.@Nullable MatchExpr expr, ItemStack stack) {
        if (expr == null) return false;
        Set<String> tags = stack.typeHolder().tags()
                .map(TagKey::location)
                .map(Identifier::toString)
                .collect(Collectors.toCollection(ObjectOpenHashSet::new));

        return expr.matches(tags);
    }

    /** 检查流体当前标签是否匹配已编译条件。空条件返回假。 */
    public static boolean tagsMatch(TagExprParser.@Nullable MatchExpr expr, FluidStack stack) {
        if (expr == null) return false;
        Set<String> tags = stack.getFluid().builtInRegistryHolder().tags()
                .map(TagKey::location)
                .map(Identifier::toString)
                .collect(Collectors.toCollection(ObjectOpenHashSet::new));

        return expr.matches(tags);
    }
}
