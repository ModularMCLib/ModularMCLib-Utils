package com.modularmc.mmclutils.utils;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 构造原版标签键并解析物品和流体的标签匹配表达式。 */
public final class TagUtils {

    private TagUtils() {}

    public static TagKey<Block> block(Identifier id) {
        return TagKey.create(Registries.BLOCK, id);
    }

    public static TagKey<Item> item(Identifier id) {
        return TagKey.create(Registries.ITEM, id);
    }

    public static TagKey<Fluid> fluid(Identifier id) {
        return TagKey.create(Registries.FLUID, id);
    }

    public static class TagExprParser {

        public enum TokenType {
            LParen,
            RParen,
            And,
            Or,
            Not,
            Xor,
            String
        }

        public static class Token {

            @Nullable
            public String lexeme;
            public TokenType type;

            public Token(TokenType type) {
                this.type = type;
            }

            public Token(TokenType type, String lexeme) {
                this.lexeme = lexeme;
                this.type = type;
            }
        }

        public static abstract class MatchExpr {

            public abstract boolean matches(Set<String> input);
        }

        private static class BinExpr extends MatchExpr {

            @Nullable
            MatchExpr left, right;
            Token op;

            public BinExpr(Token op, @Nullable MatchExpr left, @Nullable MatchExpr right) {
                this.op = op;
                this.left = left;
                this.right = right;
            }

            @Override
            public boolean matches(Set<String> input) {
                if (left == null || right == null) {
                    return false;
                }
                return switch (op.type) {
                    case And -> left.matches(input) && right.matches(input);
                    case Or -> left.matches(input) || right.matches(input);
                    case Xor -> left.matches(input) ^ right.matches(input);
                    default -> false;
                };
            }
        }

        private static class UnaryExpr extends MatchExpr {

            Token token;
            @Nullable
            MatchExpr expr;

            public UnaryExpr(Token token, @Nullable MatchExpr expr) {
                this.token = token;
                this.expr = expr;
            }

            @Override
            public boolean matches(Set<String> input) {
                if (token.type == TokenType.Not) {
                    return expr != null && !expr.matches(input);
                }

                return false;
            }
        }

        private static class StringExpr extends MatchExpr {

            @Nullable
            String value;

            public StringExpr(@Nullable String value) {
                this.value = value;
            }

            @Override
            public boolean matches(Set<String> input) {
                if (value == null || value.isEmpty()) return false;
                if (value.equals("$") && input.isEmpty()) return true;
                if (!value.contains(":") && !value.startsWith("*")) {
                    value = "c:" + value;
                }

                String val = quote(value);
                return input.stream().anyMatch(inp -> Pattern.matches(val, inp));
            }

            private String quote(String str) {
                if (str.contains("*")) {
                    var idx = str.indexOf("*");
                    if (idx == str.length() - 1) {
                        return quote(str.substring(0, idx)) + ".*";
                    } else {
                        return quote(str.substring(0, idx)) + ".*" + quote(str.substring(idx + 1));
                    }
                }

                return Pattern.quote(str);
            }
        }

        private static class GroupingExpr extends MatchExpr {

            @Nullable
            MatchExpr inner;

            public GroupingExpr(@Nullable MatchExpr inner) {
                this.inner = inner;
            }

            @Override
            public boolean matches(Set<String> input) {
                return inner != null && inner.matches(input);
            }
        }

        List<Token> tokens = Collections.emptyList();
        int idx = 0;
        @Nullable
        Token prev = null;

        @Nullable
        public MatchExpr parse(String expr) {
            tokens = tokenize(expr);

            return expression();
        }

        private boolean match(TokenType tt) {
            if (idx >= tokens.size()) {
                return false;
            }

            if (tokens.get(idx).type == tt) {
                prev = tokens.get(idx);
                idx++;
                return true;
            }

            return false;
        }

        @Nullable
        private MatchExpr expression() {
            return term();
        }

        @Nullable
        private MatchExpr term() {
            MatchExpr lhs = unary();

            BinExpr result = null;
            while (match(TokenType.And) || match(TokenType.Or) || match(TokenType.Xor)) {
                if (result == null) {
                    // noinspection DataFlowIssue 前一次 match 成功时赋值。
                    result = new BinExpr(prev, lhs, unary());
                } else {
                    result = new BinExpr(prev, result, unary());
                }
            }

            if (result != null) {
                return result;
            }

            return lhs;
        }

        @Nullable
        private MatchExpr unary() {
            if (match(TokenType.Not)) {
                // noinspection DataFlowIssue 前一次 match 成功时赋值。
                return new UnaryExpr(prev, id());
            }

            return id();
        }

        @Nullable
        private MatchExpr id() {
            if (match(TokenType.LParen)) {
                MatchExpr inner = expression();
                match(TokenType.RParen);

                return new GroupingExpr(inner);
            }

            if (match(TokenType.String)) {
                // noinspection DataFlowIssue 前一次 match 成功时赋值。
                return new StringExpr(prev.lexeme);
            }

            return null;
        }

        private List<Token> tokenize(String expr) {
            List<Token> result = new ArrayList<>();

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
                            cur != ' ') {
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
     * @return 解析结果
     */
    public static TagExprParser.@Nullable MatchExpr parseExpression(String expression) {
        return new TagExprParser().parse(expression);
    }

    /**
     * 判断物品是否满足标签表达式。
     *
     * @param expr  匹配表达式
     * @param stack 待检查物品栈
     * @return 物品的标签是否符合表达式
     */
    public static boolean tagsMatch(TagExprParser.@Nullable MatchExpr expr, ItemStack stack) {
        Set<String> tags = stack.typeHolder().tags()
                .map(TagKey::location)
                .map(Identifier::toString)
                .collect(Collectors.toSet());

        return expr != null && expr.matches(tags);
    }

    public static boolean tagsMatch(TagExprParser.@Nullable MatchExpr expr, FluidStack stack) {
        Set<String> tags = stack.getFluid().builtInRegistryHolder().tags()
                .map(TagKey::location)
                .map(Identifier::toString)
                .collect(Collectors.toSet());

        return expr != null && expr.matches(tags);
    }
}
