import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 以普通和自定义哈希策略的 int 映射为模板，生成六种原始值类型的映射。
 * 在仓库根目录执行 {@code java tools/GenerateCachedHashMaps.java}。
 */
final class GenerateCachedHashMaps {

    private static final Path DIRECTORY = Path.of("src/main/java/com/modularmc/mmclutils/api/collection/map/hash");
    private static final Pattern VALUE_RESULT = Pattern.compile(
            "\\bint (?=(?:removeEntry|removeNullEntry|addToValue|put|putIfAbsent|addTo|removeInt|getInt|getOrDefault|replace|compute\\w*|merge\\w*|getIntValue|rightInt|setValue|nextInt)\\()");
    private static final Pattern VALUE_TYPE = Pattern.compile("\\bint(?=\\[\\] (?:value|newValue)\\b| (?:v|incr|oldValue|newValue|newVal|defaultValue)\\b)");

    private enum ValueType {

        BOOLEAN("Boolean", "boolean", "Boolean", "booleans", "Predicate", "mappingFunction.test(k)", "", "false"),
        BYTE("Byte", "byte", "Byte", "bytes", "ToIntFunction", "SafeMath.safeIntToByte(mappingFunction.applyAsInt(k))", "apply", "0"),
        SHORT("Short", "short", "Short", "shorts", "ToIntFunction", "SafeMath.safeIntToShort(mappingFunction.applyAsInt(k))", "apply", "0"),
        CHAR("Char", "char", "Character", "chars", "ToIntFunction", "SafeMath.safeIntToChar(mappingFunction.applyAsInt(k))", "apply", "0"),
        FLOAT("Float", "float", "Float", "floats", "ToDoubleFunction", "SafeMath.safeDoubleToFloat(mappingFunction.applyAsDouble(k))", "apply", "0f"),
        DOUBLE("Double", "double", "Double", "doubles", "ToDoubleFunction", "mappingFunction.applyAsDouble(k)", "applyAsDouble", "0d");

        final String name;
        final String primitive;
        final String boxed;
        final String fastutilPackage;
        final String mappingFunction;
        final String readMappedValue;
        final String applyOperator;
        final String zero;

        ValueType(String name, String primitive, String boxed, String fastutilPackage,
                  String mappingFunction, String readMappedValue, String applyOperator, String zero) {
            this.name = name;
            this.primitive = primitive;
            this.boxed = boxed;
            this.fastutilPackage = fastutilPackage;
            this.mappingFunction = mappingFunction;
            this.readMappedValue = readMappedValue;
            this.applyOperator = applyOperator;
            this.zero = zero;
        }

        String hash(String value) {
            return switch (this) {
                case BOOLEAN -> "(" + value + " ? 1231 : 1237)";
                case FLOAT, DOUBLE -> "HashCommon." + primitive + "2int(" + value + ")";
                default -> value;
            };
        }

        String bits() {
            return switch (this) {
                case FLOAT -> "Float.floatToRawIntBits";
                case DOUBLE -> "Double.doubleToRawLongBits";
                default -> "";
            };
        }
    }

    public static void main(String[] args) throws IOException {
        Map<Path, String> generated = new LinkedHashMap<>();
        for (String suffix : new String[] { "OpenCachedHashMap", "OpenCustomCachedHashMap" }) {
            String template = Files.readString(DIRECTORY.resolve("Object2Int" + suffix + ".java")).replace("\r\n", "\n");
            for (ValueType type : ValueType.values()) {
                Path target = DIRECTORY.resolve("Object2" + type.name + suffix + ".java");
                generated.put(target, specialize(template, type));
            }
        }
        for (var entry : generated.entrySet()) {
            Path target = entry.getKey();
            String source = entry.getValue();
            if (Files.exists(target)) {
                String previous = Files.readString(target);
                if (previous.replace("\r\n", "\n").equals(source)) continue;
                if (previous.contains("\r\n")) source = source.replace("\n", "\r\n");
            }
            Files.writeString(target, source);
        }
    }

    private static String specialize(String source, ValueType type) {
        String name = type.name;
        String primitive = type.primitive;
        String boxed = type.boxed;

        // 布尔映射沿用 fastutil 接口，只保留装箱合并。
        if (type == ValueType.BOOLEAN) {
            source = withoutMethod(source, "    private int addToValue(");
            source = withoutMethod(source, "    @Override\n    public int addTo(");
            source = withoutMethod(source, "    @Override\n    public int mergeInt(");
        }
        source = mapCall(source, "Object2IntFunction", "mappingFunction.get" + name + "(k)");
        source = mapCall(source, "ToIntFunction", type.readMappedValue);

        // 槽位、缓存哈希和容量仍使用 int。
        source = VALUE_RESULT.matcher(source).replaceAll(primitive + " ");
        source = VALUE_TYPE.matcher(source).replaceAll(primitive);
        source = source.replace("newValue = new int[", "newValue = new " + primitive + "[")
                .replace("value[last] = 0;", "value[last] = " + type.zero + ";")
                .replace("Object2Int", "Object2" + name)
                .replace("ObjectIntPair", "Object" + name + "Pair")
                .replace("object2IntEntrySet", "object2" + name + "EntrySet")
                .replace(".ints.*;", "." + type.fastutilPackage + ".*;")
                .replace("IntCollection", name + "Collection")
                .replace("IntIterator", name + "Iterator")
                .replace("IntSpliterator", name + "Spliterator")
                .replaceAll("(get|right|next|remove|compute|merge)Int(?=Value|IfPresent|\\()", "$1" + name)
                .replaceAll("\\bInteger\\b(?!\\.MIN_VALUE)", boxed)
                .replace("ToIntFunction", type.mappingFunction);
        source = functions(source, type);

        if (type == ValueType.BYTE || type == ValueType.SHORT || type == ValueType.CHAR) {
            source = source.replace("= oldValue + incr;", "= (" + primitive + ") (oldValue + incr);")
                    .replace("= defRetValue + incr;", "= (" + primitive + ") (defRetValue + incr);");
        }
        source = source.replace("remappingFunction.apply((k), value[pos])", "remappingFunction.apply((k), " + boxed + ".valueOf(value[pos]))")
                .replace("pos >= 0 ? value[pos] : null", "pos >= 0 ? " + boxed + ".valueOf(value[pos]) : null")
                .replace("remappingFunction.apply(value[pos], v)", "remappingFunction.apply(" + boxed + ".valueOf(value[pos]), " + boxed + ".valueOf(v))")
                .replace("remappingFunction.applyAsInt(value[pos], v)", "remappingFunction." + type.applyOperator + "(value[pos], v)")
                .replace("return setValue((v).intValue());", "return " + boxed + ".valueOf(setValue((v)." + primitive + "Value()));")
                .replace("final " + primitive + " v = (" + boxed + ") (e.getValue());",
                        "final " + primitive + " v = ((" + boxed + ") (e.getValue()))." + primitive + "Value();");
        return entryForm(source, type);
    }

    private static String functions(String source, ValueType type) {
        if (type == ValueType.DOUBLE) {
            return source.replace("IntBinaryOperator", "DoubleBinaryOperator").replace("IntConsumer", "DoubleConsumer");
        }
        source = source.replace("import java.util.function.IntBinaryOperator;\n", "")
                .replace("import java.util.function.IntConsumer;\n", "")
                .replace("IntBinaryOperator", type.name + "BinaryOperator")
                .replace("IntConsumer", type.name + "Consumer");
        if (type.readMappedValue.startsWith("SafeMath")) {
            source = source.replace("import it.unimi.dsi.fastutil.HashCommon;\n",
                    "import it.unimi.dsi.fastutil.HashCommon;\nimport it.unimi.dsi.fastutil.SafeMath;\n");
        }
        if (type == ValueType.SHORT) {
            source = source.replace("import it.unimi.dsi.fastutil.shorts.*;\nimport it.unimi.dsi.fastutil.objects.*;",
                    "import it.unimi.dsi.fastutil.objects.*;\nimport it.unimi.dsi.fastutil.shorts.*;");
        }
        return source;
    }

    private static String entryForm(String source, ValueType type) {
        String unboxed = "(e.getValue())." + type.primitive + "Value()";
        String bits = type.bits();
        if (bits.isEmpty()) {
            source = source.replace("((value[index]) == (e.getValue()))", "((value[index]) == (" + unboxed + "))");
        } else {
            source = source.replace("((value[index]) == (e.getValue()))", "(" + bits + "(value[index]) == " + bits + "(" + unboxed + "))")
                    .replace("((value[n]) == (v))", "(" + bits + "(value[n]) == " + bits + "(v))")
                    .replace("((value[pos]) == (v))", "(" + bits + "(value[pos]) == " + bits + "(v))");
        }
        return source.replace("hash[index] ^ value[index]", "hash[index] ^ " + type.hash("value[index]"))
                .replace("t ^= value[i];", "t ^= " + type.hash("value[i]") + ";")
                .replace("h += value[n];", "h += " + type.hash("value[n]") + ";");
    }

    private static String withoutMethod(String source, String declaration) {
        int start = onlyIndex(source, declaration, 0);
        int end = source.indexOf("\n    }\n\n", start);
        if (end < 0) throw new IllegalStateException("找不到模板方法末尾 " + declaration);
        return source.substring(0, start) + source.substring(end + "\n    }\n\n".length());
    }

    private static String mapCall(String source, String functionType, String expression) {
        String call = "mappingFunction.applyAsInt(k)";
        int start = onlyIndex(source, functionType + "<? super K> mappingFunction)", 0);
        int end = source.indexOf("\n    }", start);
        String method = source.substring(start, end);
        int index = onlyIndex(method, call, 0);
        return source.substring(0, start + index) + expression + source.substring(start + index + call.length());
    }

    private static int onlyIndex(String source, String fragment, int from) {
        if (from < 0) throw new IllegalStateException("找不到映射方法");
        int index = source.indexOf(fragment, from);
        if (index < 0 || source.indexOf(fragment, index + fragment.length()) >= 0) {
            throw new IllegalStateException("模板片段必须恰好出现一次 " + fragment);
        }
        return index;
    }
}
