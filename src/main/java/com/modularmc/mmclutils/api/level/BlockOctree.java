package com.modularmc.mmclutils.api.level;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 按整数方块坐标存值的稀疏八叉树，每个坐标至多一个值，全部数据放在扁平数组里。功能：
 * <ul>
 * <li>{@link #insert}/{@link #get}/{@link #contains}/{@link #remove} 按坐标增删查，复杂度为 O(深度)。重复插入返回 false。越界返回 false 或
 * null</li>
 * <li>{@link #query(int, int, int, BlockRange, Predicate, Consumer)} 与收集到 {@link Collection} 的重载：按
 * {@link BlockRange}（球形或长方体）查范围内全部值，可带过滤器，不分配内存；
 * 与范围不相交的子树整棵跳过，整体落在范围内的子树不再逐个判距离</li>
 * <li>{@link #findAny(int, int, int, BlockRange, Predicate)}：范围内找到第一个满足过滤器的值就返回</li>
 * <li>{@link #findNearest(int, int, int, BlockRange, Predicate)}：范围内欧氏距离最近且满足过滤器的值，距离相同时取 x、y、z 依次最小的坐标</li>
 * <li>{@link #findAny(Predicate)}/{@link #forEach}：遍历全树；{@link #size}/{@link #isEmpty} O(1)；{@link #clear}
 * 清空；{@link #inBounds} 判断坐标是否在树覆盖的立方体内</li>
 * <li>{@link #compact()}：按 Z 序重排条目池并丢掉空洞，批量插入后调用可提升查询局部性</li>
 * <li>值不能为 null；不是线程安全的</li>
 * </ul>
 *
 * @param <T> 与坐标关联的非空值类型
 */
@SuppressWarnings("unchecked")
public final class BlockOctree<T> {

    /** 叶节点分裂前默认可以保存的位置数。 */
    public static final int DEFAULT_LEAF_CAPACITY = 32;

    private static final int INITIAL_LEAF_CAPACITY = 4;
    private static final int NODE_INTS = 8;
    private static final int LEAF = -1;
    private static final int ROOT = 0;
    private static final Object NOT_FOUND = new Object();

    private final int originX, originY, originZ;
    private final int shift;
    private final int rootSize;
    private final int leafCapacity;
    private final int initialCapacity;

    private int[] nodes;
    private int nodeTop;
    private int freeNode;

    private int[] coords;
    private Object[] values;
    private int poolTop;

    private int[] freeStart;
    private int[] freeCap;
    private int freeTop;

    private int[] scratchCoords;
    private Object[] scratchValues;
    private int[] scratchOctant;
    private int[] scratchCount;
    private int[] scratchPrefix;
    private int[] scratchCursor;

    private int allocatedCapacity;

    private int size;

    private long nearestD2;
    private int nearestX, nearestY, nearestZ;
    private Object nearestValue;
    private long lastNearestD2;
    private int lastNearestX, lastNearestY, lastNearestZ;

    /** 按 X、Z 共用的边界和独立 Y 边界创建索引，使用默认叶容量。 */
    public BlockOctree(int xzMin, int xzMax, int yMin, int yMax) {
        this(xzMin, yMin, xzMin, xzMax, yMax, xzMax, DEFAULT_LEAF_CAPACITY);
    }

    /** 使用给定的闭合包围盒和默认叶容量创建索引。 */
    public BlockOctree(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this(minX, minY, minZ, maxX, maxY, maxZ, DEFAULT_LEAF_CAPACITY);
    }

    /** 指定叶容量。边界转换为以最小坐标为原点、边长为二次幂的立方体，调用 {@link #inBounds} 查询实际范围。 */
    public BlockOctree(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int leafCapacity) {
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            throw new IllegalArgumentException("max must be >= min");
        }
        if (leafCapacity < 1) {
            throw new IllegalArgumentException("leafCapacity must be >= 1");
        }
        long span = Math.max((long) maxX - minX, Math.max((long) maxY - minY, (long) maxZ - minZ)) + 1L;
        int s = 64 - Long.numberOfLeadingZeros(span - 1L);
        if (s > 30) {
            throw new IllegalArgumentException("region span too large: " + span);
        }
        int edge = 1 << s;
        if ((long) minX + edge > Integer.MAX_VALUE + 1L ||
                (long) minY + edge > Integer.MAX_VALUE + 1L ||
                (long) minZ + edge > Integer.MAX_VALUE + 1L) {
            throw new IllegalArgumentException("region origin + cube size overflows int");
        }
        this.shift = s;
        this.originX = minX;
        this.originY = minY;
        this.originZ = minZ;
        this.leafCapacity = leafCapacity;
        this.initialCapacity = Math.min(INITIAL_LEAF_CAPACITY, leafCapacity);
        this.rootSize = edge;

        this.nodes = new int[NODE_INTS << 1];
        this.coords = new int[3 * initialCapacity];
        this.values = new Object[initialCapacity];
        this.freeStart = new int[2];
        this.freeCap = new int[2];

        nodeTop = 1;
        initLeaf(ROOT, initialCapacity);
    }

    /** 在未占用的坐标插入非空值。已有值或坐标越界时返回 {@code false}。 */
    public boolean insert(int x, int y, int z, T value) {
        if (value == null) throw new NullPointerException("value");
        int lx = x - originX, ly = y - originY, lz = z - originZ;
        if (((lx | ly | lz) >>> shift) != 0) return false;

        int node = ROOT;
        int s = shift;
        for (;;) {
            int base = node << 3;
            if (nodes[base] == LEAF) {
                int start = nodes[base + 1];
                int count = nodes[base + 2];
                for (int i = 0, j = start * 3; i < count; i++, j += 3) {
                    if (coords[j] == lx && coords[j + 1] == ly && coords[j + 2] == lz) return false;
                }
                if (count == leafCapacity) {
                    if (s == 0) return false;
                    split(node, s - 1);
                    base = node << 3;
                } else {
                    if (count == nodes[base + 3]) start = growLeaf(node, count);
                    int j = (start + count) * 3;
                    coords[j] = lx;
                    coords[j + 1] = ly;
                    coords[j + 2] = lz;
                    values[start + count] = value;
                    nodes[base + 2] = count + 1;
                    size++;
                    return true;
                }
            }
            int idx = ((lx >>> (s - 1)) & 1) | (((ly >>> (s - 1)) & 1) << 1) | (((lz >>> (s - 1)) & 1) << 2);
            s--;
            int enc = nodes[base + idx];
            if (enc == 0) {
                int child = allocNode();
                initLeaf(child, initialCapacity);
                nodes[base + idx] = child + 1;
                node = child;
            } else {
                node = enc - 1;
            }
        }
    }

    /** 读取给定坐标的值，缺失或越界时返回空值。 */
    public T get(int x, int y, int z) {
        int lx = x - originX, ly = y - originY, lz = z - originZ;
        if (((lx | ly | lz) >>> shift) != 0) return null;
        int node = ROOT;
        int s = shift;
        for (;;) {
            int base = node << 3;
            if (nodes[base] == LEAF) {
                int start = nodes[base + 1];
                int count = nodes[base + 2];
                for (int i = 0, j = start * 3; i < count; i++, j += 3) {
                    if (coords[j] == lx && coords[j + 1] == ly && coords[j + 2] == lz) {
                        return (T) values[start + i];
                    }
                }
                return null;
            }
            if (s == 0) return null;
            int idx = ((lx >>> (s - 1)) & 1) | (((ly >>> (s - 1)) & 1) << 1) | (((lz >>> (s - 1)) & 1) << 2);
            s--;
            int enc = nodes[base + idx];
            if (enc == 0) return null;
            node = enc - 1;
        }
    }

    /** 检查给定坐标是否保存了值。 */
    public boolean contains(int x, int y, int z) {
        return get(x, y, z) != null;
    }

    /** 移除并返回原值，缺失或越界时返回空值。 */
    public T remove(int x, int y, int z) {
        int lx = x - originX, ly = y - originY, lz = z - originZ;
        if (((lx | ly | lz) >>> shift) != 0) return null;
        Object removed = removeNode(ROOT, lx, ly, lz, shift);
        if (removed == NOT_FOUND) return null;
        size--;
        return (T) removed;
    }

    /** 返回八叉树中保存的值数量。 */
    public int size() {
        return size;
    }

    /** 判断是否还有任何值。 */
    public boolean isEmpty() {
        return size == 0;
    }

    /** 释放条目并重新初始化根节点。 */
    public void clear() {
        Arrays.fill(values, 0, poolTop, null);
        nodeTop = 1;
        freeNode = 0;
        poolTop = 0;
        freeTop = 0;
        size = 0;
        initLeaf(ROOT, initialCapacity);
    }

    /** 检查坐标是否在根节点覆盖的立方体内。 */
    public boolean inBounds(int x, int y, int z) {
        return (((x - originX) | (y - originY) | (z - originZ)) >>> shift) == 0;
    }

    /** 重新紧凑排列叶内条目的存储，返回写入新数组的条目数。 */
    public int compact() {
        if (size == 0) return 0;
        int slots = Math.max(64, size);
        int[] newCoords = new int[slots * 3];
        Object[] newValues = new Object[slots];
        int written = repack(ROOT, newCoords, newValues, 0);
        this.coords = newCoords;
        this.values = newValues;
        this.poolTop = written;
        this.freeTop = 0;
        return written;
    }

    private int repack(int node, int[] newCoords, Object[] newValues, int write) {
        int base = node << 3;
        if (nodes[base] == LEAF) {
            int start = nodes[base + 1];
            int count = nodes[base + 2];
            if (count > 0) {
                System.arraycopy(coords, start * 3, newCoords, write * 3, count * 3);
                System.arraycopy(values, start, newValues, write, count);
                nodes[base + 1] = write;
                nodes[base + 3] = count;
                return write + count;
            }
            nodes[base + 1] = write;
            nodes[base + 3] = 0;
            return write;
        }
        for (int i = 0; i < 8; i++) {
            int enc = nodes[base + i];
            if (enc != 0) write = repack(enc - 1, newCoords, newValues, write);
        }
        return write;
    }

    /** 将范围内通过筛选的条目交给消费端。筛选可为空，回调中不要修改树。 */
    public void query(int cx, int cy, int cz, BlockRange range, Predicate<? super T> filter, Consumer<? super T> sink) {
        if (range.isEmpty() || size == 0) return;
        query(ROOT, 0, 0, 0, rootSize, (long) cx - originX, (long) cy - originY, (long) cz - originZ, range, filter, sink, false);
    }

    /** 向集合追加匹配值并返回实际新增项数。 */
    public int query(int cx, int cy, int cz, BlockRange range, Predicate<? super T> filter, Collection<? super T> out) {
        if (range.isEmpty() || size == 0) return 0;
        int before = out.size();
        query(ROOT, 0, 0, 0, rootSize, (long) cx - originX, (long) cy - originY, (long) cz - originZ, range, filter, out::add, false);
        return out.size() - before;
    }

    /** 找到任一范围内的匹配值便返回，没有符合项时返回空值。 */
    public T findAny(int cx, int cy, int cz, BlockRange range, Predicate<? super T> filter) {
        if (range.isEmpty() || size == 0) return null;
        Object r = findAny(ROOT, 0, 0, 0, rootSize, (long) cx - originX, (long) cy - originY, (long) cz - originZ, range, filter, false);
        return r == NOT_FOUND ? null : (T) r;
    }

    /** 查找平方欧氏距离最短的范围内匹配值。此方法复用内部查询状态，不可从筛选回调再次调用。 */
    public T findNearest(int cx, int cy, int cz, BlockRange range, Predicate<? super T> filter) {
        if (range.isEmpty() || size == 0) return null;
        nearestD2 = Long.MAX_VALUE;
        nearestValue = null;
        findNearest(ROOT, 0, 0, 0, rootSize, (long) cx - originX, (long) cy - originY, (long) cz - originZ, range, filter);
        T result = (T) nearestValue;
        nearestValue = null;
        if (result != null) {
            lastNearestD2 = nearestD2;
            lastNearestX = nearestX + originX;
            lastNearestY = nearestY + originY;
            lastNearestZ = nearestZ + originZ;
        }
        return result;
    }

    long lastNearestD2() {
        return lastNearestD2;
    }

    int lastNearestX() {
        return lastNearestX;
    }

    int lastNearestY() {
        return lastNearestY;
    }

    int lastNearestZ() {
        return lastNearestZ;
    }

    /** 跨全部叶节点查找任一匹配值。没有符合项时返回空值。 */
    public T findAny(Predicate<? super T> filter) {
        if (size == 0) return null;
        Object r = findAnyAll(ROOT, filter);
        return r == NOT_FOUND ? null : (T) r;
    }

    /** 向使用方传递每个存储值。遍历期间不要结构性修改树。 */
    public void forEach(Consumer<? super T> action) {
        forEach(ROOT, action);
    }

    private static int octant(int lx, int ly, int lz, int bit) {
        return ((lx >>> bit) & 1) | (((ly >>> bit) & 1) << 1) | (((lz >>> bit) & 1) << 2);
    }

    private int allocNode() {
        int idx;
        if (freeNode != 0) {
            idx = freeNode - 1;
            freeNode = nodes[idx << 3];
        } else {
            idx = nodeTop++;
            int needed = (idx + 1) << 3;
            if (needed > nodes.length) {
                nodes = Arrays.copyOf(nodes, Math.max(needed, nodes.length << 1));
            }
        }
        int base = idx << 3;
        for (int i = 0; i < NODE_INTS; i++) nodes[base + i] = 0;
        return idx;
    }

    private void freeNode(int idx) {
        int base = idx << 3;
        nodes[base] = freeNode;
        freeNode = idx + 1;
    }

    private int allocBlock(int capacity) {
        if (freeTop > 0) {
            int top = freeTop - 1;
            int available = freeCap[top];
            if (available >= capacity) {
                freeTop = top;
                allocatedCapacity = available;
                return freeStart[top];
            }
        }
        int slots = coords.length / 3;
        if (poolTop + capacity > slots) {
            int grown = slots;
            while (grown < poolTop + capacity) grown <<= 1;
            coords = Arrays.copyOf(coords, grown * 3);
            values = Arrays.copyOf(values, grown);
        }
        int start = poolTop;
        poolTop = start + capacity;
        allocatedCapacity = capacity;
        return start;
    }

    private void releaseBlock(int start, int capacity) {
        if (freeTop == freeStart.length) {
            freeStart = Arrays.copyOf(freeStart, freeTop << 1);
            freeCap = Arrays.copyOf(freeCap, freeTop << 1);
        }
        freeStart[freeTop] = start;
        freeCap[freeTop] = capacity;
        freeTop++;
    }

    private void initLeaf(int node, int capacity) {
        int start = allocBlock(capacity);
        int base = node << 3;
        nodes[base] = LEAF;
        nodes[base + 1] = start;
        nodes[base + 2] = 0;
        nodes[base + 3] = allocatedCapacity;
    }

    private int growLeaf(int node, int count) {
        int base = node << 3;
        int oldStart = nodes[base + 1];
        int oldCapacity = nodes[base + 3];
        int wanted = Math.min(Math.max(count + 1, oldCapacity << 1), leafCapacity);
        int start = allocBlock(wanted);
        int capacity = allocatedCapacity;
        System.arraycopy(coords, oldStart * 3, coords, start * 3, count * 3);
        System.arraycopy(values, oldStart, values, start, count);
        releaseBlock(oldStart, oldCapacity);
        nodes[base + 1] = start;
        nodes[base + 3] = capacity;
        return start;
    }

    private void split(int node, int childShift) {
        int base = node << 3;
        int start = nodes[base + 1];
        int count = nodes[base + 2];
        if (scratchCoords == null) {
            scratchCoords = new int[leafCapacity * 3];
            scratchValues = new Object[leafCapacity];
            scratchOctant = new int[leafCapacity];
            scratchCount = new int[8];
            scratchPrefix = new int[8];
            scratchCursor = new int[8];
        }
        int[] octantOf = scratchOctant;
        int[] countByOctant = scratchCount;
        Arrays.fill(countByOctant, 0);
        for (int i = 0, j = start * 3; i < count; i++, j += 3) {
            int idx = octant(coords[j], coords[j + 1], coords[j + 2], childShift);
            octantOf[i] = idx;
            countByOctant[idx]++;
        }
        int[] prefix = scratchPrefix;
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            prefix[i] = sum;
            sum += countByOctant[i];
        }
        int[] cursor = scratchCursor;
        System.arraycopy(prefix, 0, cursor, 0, 8);
        int[] sortedCoords = scratchCoords;
        Object[] sortedValues = scratchValues;
        for (int i = 0, j = start * 3; i < count; i++, j += 3) {
            int pos = cursor[octantOf[i]]++;
            sortedCoords[pos * 3] = coords[j];
            sortedCoords[pos * 3 + 1] = coords[j + 1];
            sortedCoords[pos * 3 + 2] = coords[j + 2];
            sortedValues[pos] = values[start + i];
        }
        System.arraycopy(sortedCoords, 0, coords, start * 3, count * 3);
        System.arraycopy(sortedValues, 0, values, start, count);
        Arrays.fill(sortedValues, 0, count, null);

        for (int i = 0; i < 8; i++) {
            int childCount = countByOctant[i];
            if (childCount == 0) {
                nodes[base + i] = 0;
                continue;
            }
            int child = allocNode();
            int childBase = child << 3;
            nodes[childBase] = LEAF;
            nodes[childBase + 1] = start + prefix[i];
            nodes[childBase + 2] = childCount;
            nodes[childBase + 3] = childCount;
            nodes[base + i] = child + 1;
        }
    }

    private Object removeNode(int node, int lx, int ly, int lz, int s) {
        int base = node << 3;
        if (nodes[base] == LEAF) {
            int start = nodes[base + 1];
            int count = nodes[base + 2];
            for (int i = 0, j = start * 3; i < count; i++, j += 3) {
                if (coords[j] == lx && coords[j + 1] == ly && coords[j + 2] == lz) {
                    Object old = values[start + i];
                    int last = count - 1;
                    if (i != last) {
                        int lastCoord = (start + last) * 3;
                        coords[j] = coords[lastCoord];
                        coords[j + 1] = coords[lastCoord + 1];
                        coords[j + 2] = coords[lastCoord + 2];
                        values[start + i] = values[start + last];
                    }
                    values[start + last] = null;
                    nodes[base + 2] = last;
                    return old;
                }
            }
            return NOT_FOUND;
        }
        if (s == 0) return NOT_FOUND;
        int idx = ((lx >>> (s - 1)) & 1) | (((ly >>> (s - 1)) & 1) << 1) | (((lz >>> (s - 1)) & 1) << 2);
        int enc = nodes[base + idx];
        if (enc == 0) return NOT_FOUND;
        int child = enc - 1;
        Object removed = removeNode(child, lx, ly, lz, s - 1);
        if (removed != NOT_FOUND) {
            int childBase = child << 3;
            if (nodes[childBase] == LEAF && nodes[childBase + 2] == 0) {
                releaseBlock(nodes[childBase + 1], nodes[childBase + 3]);
                freeNode(child);
                nodes[base + idx] = 0;
            }
        }
        return removed;
    }

    private static long gap(long c, int lo, int hi) {
        return c < lo ? lo - c : (c > hi ? c - hi : 0);
    }

    private static long far(long c, int lo, int hi) {
        return Math.max(Math.abs(c - lo), Math.abs(c - hi));
    }

    private void query(int node, int ox, int oy, int oz, int size, long cx, long cy, long cz, BlockRange range,
                       Predicate<? super T> filter, Consumer<? super T> sink, boolean inside) {
        int hiX = ox | (size - 1), hiY = oy | (size - 1), hiZ = oz | (size - 1);
        if (!inside) {
            if (!range.reaches(gap(cx, ox, hiX), gap(cy, oy, hiY), gap(cz, oz, hiZ))) return;
            inside = range.reaches(far(cx, ox, hiX), far(cy, oy, hiY), far(cz, oz, hiZ));
        }
        int base = node << 3;
        if (nodes[base] == LEAF) {
            int start = nodes[base + 1];
            int count = nodes[base + 2];
            if (inside) {
                for (int i = 0; i < count; i++) {
                    T v = (T) values[start + i];
                    if (filter == null || filter.test(v)) sink.accept(v);
                }
            } else {
                for (int i = 0, j = start * 3; i < count; i++, j += 3) {
                    if (!range.reaches(Math.abs(coords[j] - cx), Math.abs(coords[j + 1] - cy), Math.abs(coords[j + 2] - cz))) continue;
                    T v = (T) values[start + i];
                    if (filter == null || filter.test(v)) sink.accept(v);
                }
            }
            return;
        }
        int half = size >>> 1;
        for (int i = 0; i < 8; i++) {
            int enc = nodes[base + i];
            if (enc == 0) continue;
            query(enc - 1,
                    ox + ((i & 1) == 0 ? 0 : half),
                    oy + ((i & 2) == 0 ? 0 : half),
                    oz + ((i & 4) == 0 ? 0 : half),
                    half, cx, cy, cz, range, filter, sink, inside);
        }
    }

    private Object findAny(int node, int ox, int oy, int oz, int size, long cx, long cy, long cz, BlockRange range,
                           Predicate<? super T> filter, boolean inside) {
        int hiX = ox | (size - 1), hiY = oy | (size - 1), hiZ = oz | (size - 1);
        if (!inside) {
            if (!range.reaches(gap(cx, ox, hiX), gap(cy, oy, hiY), gap(cz, oz, hiZ))) return NOT_FOUND;
            inside = range.reaches(far(cx, ox, hiX), far(cy, oy, hiY), far(cz, oz, hiZ));
        }
        int base = node << 3;
        if (nodes[base] == LEAF) {
            int start = nodes[base + 1];
            int count = nodes[base + 2];
            for (int i = 0, j = start * 3; i < count; i++, j += 3) {
                if (!inside && !range.reaches(Math.abs(coords[j] - cx), Math.abs(coords[j + 1] - cy), Math.abs(coords[j + 2] - cz))) continue;
                Object v = values[start + i];
                if (filter == null || filter.test((T) v)) return v;
            }
            return NOT_FOUND;
        }
        int half = size >>> 1;
        for (int i = 0; i < 8; i++) {
            int enc = nodes[base + i];
            if (enc == 0) continue;
            Object r = findAny(enc - 1,
                    ox + ((i & 1) == 0 ? 0 : half),
                    oy + ((i & 2) == 0 ? 0 : half),
                    oz + ((i & 4) == 0 ? 0 : half),
                    half, cx, cy, cz, range, filter, inside);
            if (r != NOT_FOUND) return r;
        }
        return NOT_FOUND;
    }

    private void findNearest(int node, int ox, int oy, int oz, int size, long cx, long cy, long cz, BlockRange range,
                             Predicate<? super T> filter) {
        int hiX = ox | (size - 1), hiY = oy | (size - 1), hiZ = oz | (size - 1);
        long mx = gap(cx, ox, hiX), my = gap(cy, oy, hiY), mz = gap(cz, oz, hiZ);
        if (!range.reaches(mx, my, mz)) return;
        if (mx * mx + my * my + mz * mz > nearestD2) return;
        int base = node << 3;
        if (nodes[base] == LEAF) {
            int start = nodes[base + 1];
            int count = nodes[base + 2];
            for (int i = 0, j = start * 3; i < count; i++, j += 3) {
                int x = coords[j], y = coords[j + 1], z = coords[j + 2];
                long dx = Math.abs(x - cx), dy = Math.abs(y - cy), dz = Math.abs(z - cz);
                if (!range.reaches(dx, dy, dz)) continue;
                long d2 = dx * dx + dy * dy + dz * dz;
                if (d2 > nearestD2) continue;
                if (d2 == nearestD2 && nearestValue != null && !lexLess(x, y, z)) continue;
                T v = (T) values[start + i];
                if (filter != null && !filter.test(v)) continue;
                nearestD2 = d2;
                nearestX = x;
                nearestY = y;
                nearestZ = z;
                nearestValue = v;
            }
            return;
        }
        int half = size >>> 1;
        for (int i = 0; i < 8; i++) {
            int enc = nodes[base + i];
            if (enc == 0) continue;
            findNearest(enc - 1,
                    ox + ((i & 1) == 0 ? 0 : half),
                    oy + ((i & 2) == 0 ? 0 : half),
                    oz + ((i & 4) == 0 ? 0 : half),
                    half, cx, cy, cz, range, filter);
        }
    }

    private boolean lexLess(int x, int y, int z) {
        if (x != nearestX) return x < nearestX;
        if (y != nearestY) return y < nearestY;
        return z < nearestZ;
    }

    private Object findAnyAll(int node, Predicate<? super T> filter) {
        int base = node << 3;
        if (nodes[base] == LEAF) {
            int start = nodes[base + 1];
            int count = nodes[base + 2];
            for (int i = 0; i < count; i++) {
                Object v = values[start + i];
                if (filter == null || filter.test((T) v)) return v;
            }
            return NOT_FOUND;
        }
        for (int i = 0; i < 8; i++) {
            int enc = nodes[base + i];
            if (enc != 0) {
                Object r = findAnyAll(enc - 1, filter);
                if (r != NOT_FOUND) return r;
            }
        }
        return NOT_FOUND;
    }

    private void forEach(int node, Consumer<? super T> action) {
        int base = node << 3;
        if (nodes[base] == LEAF) {
            int start = nodes[base + 1];
            int count = nodes[base + 2];
            for (int i = 0; i < count; i++) action.accept((T) values[start + i]);
            return;
        }
        for (int i = 0; i < 8; i++) {
            int enc = nodes[base + i];
            if (enc != 0) forEach(enc - 1, action);
        }
    }
}
