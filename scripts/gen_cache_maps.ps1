# 以普通和自定义策略的 Int 缓存 Map 为模板，重建六组原始类型容器。
# 先用 -OutputDirectory 输出到临时目录，可检查差异后再写回生产目录。
param([string]$OutputDirectory)

$ErrorActionPreference = 'Stop'
$srcDir = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')).Path 'src\main\java\com\modularmc\mmclutils\collection\map\hash'

if (-not $outputDirectory) {
    $outputDirectory = $srcDir
} else {
    $outputDirectory = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($outputDirectory)
    [IO.Directory]::CreateDirectory($outputDirectory) | Out-Null
}

function Read-Template($name) {
    Get-Content -LiteralPath (Join-Path $srcDir "$name.java") -Raw -Encoding UTF8
}

function Write-File($path, $content) {
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($path, $content, $utf8)
}

# 各原始类型的值、装箱类型和运算接口。
$types = @(
    @{ Code = 'Z'; Name = 'Boolean';   Pkg = 'booleans'; Value = 'boolean'; Wrapper = 'Boolean';   Prim = 'boolean'; Consumer = 'BooleanConsumer';   Operator = $null },
    @{ Code = 'B'; Name = 'Byte';      Pkg = 'bytes';    Value = 'byte';    Wrapper = 'Byte';      Prim = 'byte';    Consumer = 'ByteConsumer';      Operator = 'it.unimi.dsi.fastutil.bytes.ByteBinaryOperator' },
    @{ Code = 'S'; Name = 'Short';     Pkg = 'shorts';   Value = 'short';   Wrapper = 'Short';     Prim = 'short';    Consumer = 'ShortConsumer';     Operator = 'it.unimi.dsi.fastutil.shorts.ShortBinaryOperator' },
    @{ Code = 'D'; Name = 'Double';    Pkg = 'doubles';  Value = 'double';  Wrapper = 'Double';    Prim = 'double';   Consumer = 'java.util.function.DoubleConsumer'; Operator = 'java.util.function.DoubleBinaryOperator' },
    @{ Code = 'F'; Name = 'Float';     Pkg = 'floats';   Value = 'float';   Wrapper = 'Float';     Prim = 'float';    Consumer = 'FloatConsumer';     Operator = 'it.unimi.dsi.fastutil.floats.FloatBinaryOperator' },
    @{ Code = 'C'; Name = 'Char';      Pkg = 'chars';    Value = 'char';    Wrapper = 'Character'; Prim = 'char';     Consumer = 'CharConsumer';      Operator = 'it.unimi.dsi.fastutil.chars.CharBinaryOperator' }
)

function Transform($content, $t, $isCustom) {
    # 统一模板换行，使固定方法体可以准确匹配。
    $content = $content -replace "`r`n", "`n"
    $code = $t.Code; $name = $t.Name; $pkg = $t.Pkg; $value = $t.Value
    $wrapper = $t.Wrapper; $prim = $t.Prim; $consumer = $t.Consumer; $op = $t.Operator
    $className = "Object2${name}Open$(if ($isCustom) { 'Custom' })CachedHashMap"
    $hashLine = if ($isCustom) { '            h = strategy.hashCode(k);' } else { '            h = k.hashCode();' }

    # 改写基本类型的映射接口、参数和返回值。

    # 先调整接收 JDK 函数的 computeIfAbsent。
    $toIntBlock = @"
    @Override
    public int computeIfAbsent(final K k, final ToIntFunction<? super K> mappingFunction) {
        int pos, h = 0;
        if (k == null) {
            pos = containsNullKey ? n : -(n + 1);
        } else {
$hashLine
            pos = find(k, h);
        }
        if (pos >= 0) return value[pos];
        final int newValue = mappingFunction.applyAsInt(k);
        insert(-pos - 1, k, newValue, h);
        return newValue;
    }
"@
    $repl = $null
    if ($code -eq 'Z') {
        $repl = @"
    @Override
    public boolean computeIfAbsent(final K k, final Predicate<? super K> mappingFunction) {
        int pos, h = 0;
        if (k == null) {
            pos = containsNullKey ? n : -(n + 1);
        } else {
$hashLine
            pos = find(k, h);
        }
        if (pos >= 0) return value[pos];
        final boolean newValue = mappingFunction.test(k);
        insert(-pos - 1, k, newValue, h);
        return newValue;
    }
"@
    } elseif ($code -eq 'D') {
        $repl = $toIntBlock.Replace('public int computeIfAbsent(final K k, final ToIntFunction<? super K> mappingFunction) {', 'public double computeIfAbsent(final K k, final ToDoubleFunction<? super K> mappingFunction) {')
        $repl = $repl.Replace('final int newValue = mappingFunction.applyAsInt(k);', 'final double newValue = mappingFunction.applyAsDouble(k);')
    } elseif ($code -eq 'F') {
        $repl = $toIntBlock.Replace('public int computeIfAbsent(final K k, final ToIntFunction<? super K> mappingFunction) {', 'public float computeIfAbsent(final K k, final ToDoubleFunction<? super K> mappingFunction) {')
        $repl = $repl.Replace('final int newValue = mappingFunction.applyAsInt(k);', 'final float newValue = SafeMath.safeDoubleToFloat(mappingFunction.applyAsDouble(k));')
    } else {
        # byte、short 和 char 保留 ToIntFunction。
        $repl = $toIntBlock.Replace('public int computeIfAbsent(final K k, final ToIntFunction<? super K> mappingFunction) {', "public ${value} computeIfAbsent(final K k, final ToIntFunction<? super K> mappingFunction) {")
        if ($code -eq 'B') {
            $repl = $repl.Replace('final int newValue = mappingFunction.applyAsInt(k);', 'final byte newValue = SafeMath.safeIntToByte(mappingFunction.applyAsInt(k));')
        } elseif ($code -eq 'S') {
            $repl = $repl.Replace('final int newValue = mappingFunction.applyAsInt(k);', 'final short newValue = SafeMath.safeIntToShort(mappingFunction.applyAsInt(k));')
        } elseif ($code -eq 'C') {
            $repl = $repl.Replace('final int newValue = mappingFunction.applyAsInt(k);', 'final char newValue = SafeMath.safeIntToChar(mappingFunction.applyAsInt(k));')
        }
    }
    if ($content.IndexOf($toIntBlock, [StringComparison]::Ordinal) -lt 0) {
        throw "找不到 $className 生成模板中的 computeIfAbsent"
    }
    $content = $content.Replace($toIntBlock, $repl)

    # 布尔映射没有加法和数值 merge 重载。
    if ($code -eq 'Z') {
        # 移除私有加值方法。
        $content = [regex]::Replace($content, '(?m)^    private int addToValue\(final int pos, final int incr\) \{\r?\n(.*\r?\n)*?    \}\r?\n', '')
        # 移除按键加值方法。
        $content = [regex]::Replace($content, '(?m)^    @Override\r?\n    public int addTo\(final K k, final int incr\) \{\r?\n(.*\r?\n)*?    \}\r?\n', '')
        # 移除基本类型合并方法。
        $content = [regex]::Replace($content, '(?m)^(?:    /\*\* \{@inheritDoc\} \*/\r?\n)?    @Override\r?\n    public \w+ merge\w+\(final K k, final \w+ v, [\w.]+BinaryOperator remappingFunction\) \{\r?\n(.*\r?\n)*?    \}\r?\n', '')
    }

    # 调整类名、父类和导入。
    $content = $content.Replace('Object2IntOpenCachedHashMap', $className)
    $content = $content.Replace('Object2IntOpenCustomCachedHashMap', $className)
    if ($isCustom) {
        $content = $content.Replace("class ${className}<K> extends Object2IntOpenCustomHashMap<K>", "class ${className}<K> extends Object2${name}OpenCustomHashMap<K>")
    } else {
        $content = $content.Replace("class ${className}<K> extends Object2IntOpenHashMap<K>", "class ${className}<K> extends Object2${name}OpenHashMap<K>")
    }
    # 装箱类型只出现在值的位置。
    # 先处理下界参数。
    # 上界参数独立处理。
    $content = $content.Replace('? super Integer', "? super ${wrapper}")
    $content = $content.Replace('? extends Integer', "? extends ${wrapper}")
    $content = $content.Replace('Map.Entry<K, Integer>', "Map.Entry<K, ${wrapper}>")
    $content = $content.Replace('it.unimi.dsi.fastutil.ints.*', "it.unimi.dsi.fastutil.${pkg}.*")
    $content = $content.Replace('Object2IntMap', "Object2${name}Map")
    $content = $content.Replace('Object2IntFunction', "Object2${name}Function")
    $content = $content.Replace('ObjectIntPair', "Object${name}Pair")
    $content = $content.Replace('IntIterator', "${name}Iterator")
    $content = $content.Replace('IntSpliterator', "${name}Spliterator")
    $content = $content.Replace('IntSpliterators', "${name}Spliterators")
    $content = $content.Replace('IntCollection', "${name}Collection")
    $content = $content.Replace('AbstractIntCollection', "Abstract${name}Collection")
    $content = $content.Replace("import java.util.function.IntConsumer;`n", '')
    $content = $content.Replace('IntConsumer', ($consumer -split '\.')[-1])
    if ($consumer.Contains('.')) {
        $content = $content.Replace('import java.util.function.Consumer;', "import java.util.function.Consumer;`nimport $consumer;")
    }

    # 值数组按类型生成，键哈希始终为 int。
    $content = $content.Replace('int[] value', "${value}[] value")
    $content = $content.Replace('final int[] newValue = new int[newN + 1];', "final ${value}[] newValue = new ${value}[newN + 1];")

    # 替换与值类型绑定的方法名。
    $content = $content.Replace('removeInt', "remove${name}")
    $content = $content.Replace('getIntValue', "get${name}Value")
    $content = $content.Replace('rightInt', "right${name}")
    $content = $content.Replace('computeIntIfPresent', "compute${name}IfPresent")
    $content = $content.Replace('computeInt', "compute${name}")
    $content = $content.Replace('mergeInt', "merge${name}")
    $content = $content.Replace('getInt', "get${name}")
    $content = $content.Replace('nextInt', "next${name}")
    $content = $content.Replace('object2IntEntrySet', "object2${name}EntrySet")

    # 替换值类型参数和局部变量。
    $content = $content.Replace('private int removeEntry(int pos)', "private ${value} removeEntry(int pos)")
    $content = $content.Replace('private int removeNullEntry()', "private ${value} removeNullEntry()")
    $content = $content.Replace('private int addToValue(final int pos, final int incr)', "private ${value} addToValue(final int pos, final ${value} incr)")
    $content = $content.Replace('private void insert(final int pos, final K k, final int v, final int h)', "private void insert(final int pos, final K k, final ${value} v, final int h)")
    $content = $content.Replace('public int put(final K k', "public ${value} put(final K k")
    $content = $content.Replace('public int putIfAbsent(final K k', "public ${value} putIfAbsent(final K k")
    $content = $content.Replace('public int addTo(final K k', "public ${value} addTo(final K k")
    $content = $content.Replace('public int replace(final K k, final int oldValue, final int v)', "public ${value} replace(final K k, final ${value} oldValue, final ${value} v)")
    $content = $content.Replace('public int replace(final K k, final int v)', "public ${value} replace(final K k, final ${value} v)")
    $content = $content.Replace('public boolean remove(final Object k, final int v)', "public boolean remove(final Object k, final ${value} v)")
    $content = $content.Replace('public int getOrDefault(final Object k, final int defaultValue)', "public ${value} getOrDefault(final Object k, final ${value} defaultValue)")
    $content = $content.Replace('public int remove' + $name + '(final Object k)', "public ${value} remove${name}(final Object k)")
    $content = $content.Replace('public int get' + $name + '(final Object k)', "public ${value} get${name}(final Object k)")
    $content = $content.Replace('public int compute' + $name + 'IfPresent(final K k', "public ${value} compute${name}IfPresent(final K k")
    $content = $content.Replace('public int compute' + $name + '(final K k', "public ${value} compute${name}(final K k")
    $content = $content.Replace('public int merge' + $name + '(final K k, final int v', "public ${value} merge${name}(final K k, final ${value} v")
    $content = $content.Replace('public int merge(final K k, final int v', "public ${value} merge(final K k, final ${value} v")
    $content = $content.Replace('public int computeIfAbsent(final K k, final Object2' + $name + 'Function', "public ${value} computeIfAbsent(final K k, final Object2${name}Function")
    $content = $content.Replace('public int get' + $name + 'Value()', "public ${value} get${name}Value()")
    $content = $content.Replace('public int right' + $name + '()', "public ${value} right${name}()")
    $content = $content.Replace('public int setValue(final int v)', "public ${value} setValue(final ${value} v)")
    $content = $content.Replace('public Object' + $name + 'Pair<K> right(final int v)', "public Object${name}Pair<K> right(final ${value} v)")
    $content = $content.Replace('public int next' + $name + '()', "public ${value} next${name}()")
    $content = $content.Replace('public boolean contains(int v)', "public boolean contains(${value} v)")
    # 先转换条目视图里的装箱值。
    $content = $content.Replace('final int v = (Integer) (e.getValue());', "final ${value} v = ((${wrapper}) (e.getValue())).${prim}Value();")
    $content = $content.Replace('!(e.getValue() instanceof Integer)', "!(e.getValue() instanceof ${wrapper})")
    # 转换其余值参数。
    $content = $content.Replace('final int v', "final ${value} v")
    $content = $content.Replace('final int oldValue', "final ${value} oldValue")
    $content = $content.Replace('final int incr', "final ${value} incr")
    $content = $content.Replace('int newVal = newValue;', "${value} newVal = newValue;")
    $content = $content.Replace('final int newValue = mappingFunction.applyAsInt(k);', "final ${value} newValue = mappingFunction.get${name}(k);")
    $content = $content.Replace('final int newValue = remappingFunction.applyAsInt(value[pos], v);', "final ${value} newValue = remappingFunction.apply(value[pos], v);")
    # 调整装箱合并与计算。
    $content = $content.Replace('final Integer newValue = remappingFunction.apply((k), value[pos]);', "final ${wrapper} newValue = remappingFunction.apply((k), ${wrapper}.valueOf(value[pos]));")
    $content = $content.Replace('final Integer newValue = remappingFunction.apply((k), pos >= 0 ? value[pos] : null);', "final ${wrapper} newValue = remappingFunction.apply((k), pos >= 0 ? ${wrapper}.valueOf(value[pos]) : null);")
    $content = $content.Replace('final Integer newValue = remappingFunction.apply(value[pos], v);', "final ${wrapper} newValue = remappingFunction.apply(${wrapper}.valueOf(value[pos]), ${wrapper}.valueOf(v));")

    # 更新条目的相等比较。
    if ($code -eq 'D') {
        $content = $content.Replace('((value[index]) == (e.getValue()))', '(Double.doubleToRawLongBits(value[index]) == Double.doubleToRawLongBits((e.getValue()).doubleValue()))')
    } elseif ($code -eq 'F') {
        $content = $content.Replace('((value[index]) == (e.getValue()))', '(Float.floatToRawIntBits(value[index]) == Float.floatToRawIntBits((e.getValue()).floatValue()))')
    } else {
        $content = $content.Replace('((value[index]) == (e.getValue()))', "((value[index]) == ((e.getValue()).${prim}Value()))")
    }

    # 窄整数加法需转换回值类型。
    if ($code -in @('B', 'S', 'C')) {
        $cast = '(' + $value + ')'
        $content = $content.Replace('value[pos] = oldValue + incr;', "value[pos] = ${cast}(oldValue + incr);")
        $content = $content.Replace('value[pos] = defRetValue + incr;', "value[pos] = ${cast}(defRetValue + incr);")
    }

    # 浮点按位比较，布尔沿用标准哈希。
    if ($code -eq 'D') {
        $content = $content.Replace('((value[n]) == (v))', '(Double.doubleToRawLongBits(value[n]) == Double.doubleToRawLongBits(v))')
        $content = $content.Replace('((value[pos]) == (v))', '(Double.doubleToRawLongBits(value[pos]) == Double.doubleToRawLongBits(v))')
        $content = $content.Replace('t ^= value[i];', 't ^= HashCommon.double2int(value[i]);')
        $content = $content.Replace('h += value[n];', 'h += HashCommon.double2int(value[n]);')
        $content = $content.Replace('hash[index] ^ value[index];', 'hash[index] ^ HashCommon.double2int(value[index]);')
        $content = $content.Replace('value[last] = 0;', 'value[last] = 0d;')
        $content = $content.Replace('final double newValue = remappingFunction.apply(value[pos], v);', 'final double newValue = remappingFunction.applyAsDouble(value[pos], v);')
    } elseif ($code -eq 'F') {
        $content = $content.Replace('((value[n]) == (v))', '(Float.floatToRawIntBits(value[n]) == Float.floatToRawIntBits(v))')
        $content = $content.Replace('((value[pos]) == (v))', '(Float.floatToRawIntBits(value[pos]) == Float.floatToRawIntBits(v))')
        $content = $content.Replace('t ^= value[i];', 't ^= HashCommon.float2int(value[i]);')
        $content = $content.Replace('h += value[n];', 'h += HashCommon.float2int(value[n]);')
        $content = $content.Replace('hash[index] ^ value[index];', 'hash[index] ^ HashCommon.float2int(value[index]);')
        $content = $content.Replace('value[last] = 0;', 'value[last] = 0f;')
    } elseif ($code -eq 'Z') {
        $content = $content.Replace('t ^= value[i];', 't ^= (value[i] ? 1231 : 1237);')
        $content = $content.Replace('h += value[n];', 'h += (value[n] ? 1231 : 1237);')
        $content = $content.Replace('hash[index] ^ value[index];', 'hash[index] ^ (value[index] ? 1231 : 1237);')
        $content = $content.Replace('value[last] = 0;', 'value[last] = false;')
    }

    # 调整合并操作符类型。
    $content = $content.Replace("import java.util.function.IntBinaryOperator;`n", '')
    if ($code -ne 'Z') {
        $content = $content.Replace('IntBinaryOperator', ($op -split '\.')[-1])
        if ($code -eq 'D') {
            $content = $content.Replace('import java.util.function.Consumer;', "import java.util.function.Consumer;`nimport $op;")
        }
    }
    if ($code -in @('Z','D','F')) {
        $function = if ($code -eq 'Z') { 'Predicate' } else { 'ToDoubleFunction' }
        $content = $content.Replace('import java.util.function.ToIntFunction;', "import java.util.function.$function;")
    }
    if ($code -in @('B','S','C','F')) {
        $content = $content.Replace('import it.unimi.dsi.fastutil.HashCommon;', "import it.unimi.dsi.fastutil.HashCommon;`nimport it.unimi.dsi.fastutil.SafeMath;")
    }

    # 保留装箱条目接口。
    $content = $content.Replace('public Integer getValue()', "public ${wrapper} getValue()")
    $content = $content.Replace('public Integer setValue(final Integer v)', "public ${wrapper} setValue(final ${wrapper} v)")
    $content = $content.Replace('return setValue((v).intValue());', "return ${wrapper}.valueOf(setValue((v).${prim}Value()));")

    # 写入时统一使用 LF。
    return $content
}

foreach ($t in $types) {
    $normalName = "Object2$($t.Name)OpenCachedHashMap"
    $customName = "Object2$($t.Name)OpenCustomCachedHashMap"

    $n = Transform (Read-Template 'Object2IntOpenCachedHashMap') $t $false
    Write-File (Join-Path $outputDirectory "$normalName.java") $n

    $c = Transform (Read-Template 'Object2IntOpenCustomCachedHashMap') $t $true
    Write-File (Join-Path $outputDirectory "$customName.java") $c

    Write-Host "generated $normalName / $customName"
}
