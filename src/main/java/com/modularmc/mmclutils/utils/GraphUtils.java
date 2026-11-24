package com.modularmc.mmclutils.utils;

import it.unimi.dsi.fastutil.doubles.DoubleHeapIndirectPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CancellationException;

/** 为节点布置层级，并在二维矩形之间计算可复用的水平及竖直折线。 */
public final class GraphUtils {

    private GraphUtils() {}

    /**
     * 按边方向将有向无环图分层。层内维持同一轮的入队顺序。
     *
     * @param nodeCount 节点数，节点编号从零开始
     * @param edges     有向边，端点均须小于节点数
     * @return 从源节点一侧开始排列的各层
     * @throws IllegalArgumentException 节点数为负、边的端点越界或存在环
     */
    public static ObjectList<IntList> layers(int nodeCount, Iterable<Edge> edges) {
        if (nodeCount < 0) throw new IllegalArgumentException("节点数须非负");
        var outgoing = new IntArrayList[nodeCount];
        var pending = new IntArrayFIFOQueue();
        var degree = new int[nodeCount];
        var ranks = new int[nodeCount];
        for (Edge edge : edges) {
            int from = edge.from(), to = edge.to();
            if (from < 0 || from >= nodeCount || to < 0 || to >= nodeCount) {
                throw new IllegalArgumentException("有向边的端点超出范围 " + edge);
            }
            if (outgoing[from] == null) outgoing[from] = new IntArrayList();
            outgoing[from].add(to);
            degree[to]++;
        }
        for (int node = 0; node < nodeCount; node++) if (degree[node] == 0) pending.enqueue(node);
        var layers = new ObjectArrayList<IntArrayList>();
        int visited = 0;
        while (!pending.isEmpty()) {
            int node = pending.dequeueInt();
            int rank = ranks[node];
            if (layers.size() == rank) layers.add(new IntArrayList());
            layers.get(rank).add(node);
            visited++;
            IntArrayList next = outgoing[node];
            if (next == null) continue;
            for (int index = 0; index < next.size(); index++) {
                int child = next.getInt(index);
                ranks[child] = Math.max(ranks[child], rank + 1);
                if (--degree[child] == 0) pending.enqueue(child);
            }
        }
        if (visited != nodeCount) throw new IllegalArgumentException("图中存在未分组的环");
        var result = new ObjectArrayList<IntList>(layers.size());
        for (IntArrayList layer : layers) result.add(IntList.of(layer.toIntArray()));
        return ObjectList.of(result.toArray(IntList[]::new));
    }

    /**
     * 分层并从左到右摆放有向无环图。层内节点按 {@link #layers} 的顺序垂直排列。
     *
     * @param nodes    按节点编号排列的尺寸
     * @param edges    用节点编号表示的有向边
     * @param layerGap 相邻层之间的水平距离
     * @param nodeGap  同一层内节点之间的竖直距离
     * @return 按节点编号排列的矩形，可直接用作 {@link #route} 的障碍
     */
    public static ObjectList<Bounds> layout(ObjectList<Size> nodes, Iterable<Edge> edges, double layerGap, double nodeGap) {
        return place(nodes, layers(nodes.size(), edges), layerGap, nodeGap);
    }

    /**
     * 按调用方指定的层和顺序布置节点。可先将成环的节点分组，再指定各层的位置。
     * 各层从 X 为零处向右排布，层内沿 Y 轴向下排列并围绕 Y 为零处居中。
     *
     * @param nodes    按节点编号排列的尺寸
     * @param layers   按从左到右排列的层，各节点编号须出现一次
     * @param layerGap 相邻非空层之间的水平距离
     * @param nodeGap  同一层内节点之间的竖直距离
     * @return 按节点编号排列的矩形
     * @throws IllegalArgumentException 节点编号重复、遗漏或越界，或者间距不是有限非负值
     */
    public static ObjectList<Bounds> place(ObjectList<Size> nodes, ObjectList<? extends IntList> layers, double layerGap, double nodeGap) {
        if (!(layerGap >= 0 && nodeGap >= 0) || !Double.isFinite(layerGap) || !Double.isFinite(nodeGap)) {
            throw new IllegalArgumentException("图布局间距须为有限非负值");
        }
        int nodeCount = nodes.size();
        var placed = new Bounds[nodeCount];
        int remaining = nodeCount;
        double x = 0;
        for (IntList layer : layers) {
            if (layer.isEmpty()) continue;
            double width = 0;
            double height = (layer.size() - 1) * nodeGap;
            for (int index = 0; index < layer.size(); index++) {
                int node = layer.getInt(index);
                if (node < 0 || node >= nodeCount) throw new IllegalArgumentException("节点编号超出范围 " + node);
                Size size = nodes.get(node);
                width = Math.max(width, size.width());
                height += size.height();
            }
            double y = -height / 2;
            for (int index = 0; index < layer.size(); index++) {
                int node = layer.getInt(index);
                if (placed[node] != null) throw new IllegalArgumentException("重复放置节点 " + node);
                Size size = nodes.get(node);
                double left = x + (width - size.width()) / 2;
                placed[node] = new Bounds(left, y, left + size.width(), y + size.height());
                y += size.height() + nodeGap;
                remaining--;
            }
            x += width + layerGap;
        }
        if (remaining != 0) throw new IllegalArgumentException("节点分层遗漏了 " + remaining + " 个节点");
        return ObjectList.of(placed);
    }

    /**
     * 计算避开矩形内部的最短正交折线。允许沿边界经过，等长折线可以择其一。
     *
     * @param start     起点
     * @param end       终点
     * @param obstacles 须绕过的闭合矩形
     * @return 依次包含起点、折点及终点的列表。起终点重合时返回一个点，无路径时返回空列表
     * @throws CancellationException 寻路期间当前线程被中断
     */
    public static ObjectList<Point> route(Point start, Point end, Collection<Bounds> obstacles) {
        if (start.equals(end)) {
            for (Bounds obstacle : obstacles) if (obstacle.containsInterior(start)) return ObjectList.of();
            return ObjectList.of(start);
        }
        Bounds[] bounds = obstacles.toArray(Bounds[]::new);
        int length = Math.addExact(2, Math.multiplyExact(bounds.length, 2));
        double[] xs = new double[length], ys = new double[length];
        xs[0] = start.x();
        xs[1] = end.x();
        ys[0] = start.y();
        ys[1] = end.y();
        int index = 2;
        for (Bounds obstacle : bounds) {
            if (obstacle.containsInterior(start) || obstacle.containsInterior(end)) return ObjectList.of();
            xs[index] = obstacle.left();
            ys[index++] = obstacle.top();
            xs[index] = obstacle.right();
            ys[index++] = obstacle.bottom();
        }
        return new Grid(sortDistinct(xs), sortDistinct(ys), bounds).route(start, end);
    }

    private static double[] sortDistinct(double[] coordinates) {
        Arrays.sort(coordinates);
        int size = 1;
        for (int i = 1; i < coordinates.length; i++) {
            double coordinate = coordinates[i];
            if (Double.compare(coordinate, coordinates[size - 1]) != 0) coordinates[size++] = coordinate;
        }
        return size == coordinates.length ? coordinates : Arrays.copyOf(coordinates, size);
    }

    /** 连接源节点到目标节点的有向边，节点用从零开始的整数下标表示。 */
    public record Edge(int from, int to) {}

    /**
     * 布局使用的节点尺寸，两轴均须为有限正值。
     *
     * @param width  节点宽度
     * @param height 节点高度
     */
    public record Size(double width, double height) {

        /** @throws IllegalArgumentException 任一轴尺寸小于等于零或非有限值 */
        public Size {
            if (!(width > 0 && height > 0) || !Double.isFinite(width) || !Double.isFinite(height)) {
                throw new IllegalArgumentException("节点尺寸须为有限正值");
            }
        }
    }

    /** 二维平面中的点。坐标须为有限值。 */
    public record Point(double x, double y) {

        /** 创建平面点，两个坐标均须为有限数值。 */
        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("折线坐标须为有限值");
        }
    }

    /** 平面上的闭合矩形。坐标须为有限值且左上角不超过右下角。 */
    public record Bounds(double left, double top, double right, double bottom) {

        /** 创建面积为正的矩形，四条边必须是有限数值。 */
        public Bounds {
            if (!(left < right && top < bottom) || !Double.isFinite(left) || !Double.isFinite(top) || !Double.isFinite(right) || !Double.isFinite(bottom)) {
                throw new IllegalArgumentException("折线路由的阻挡矩形无效");
            }
        }

        /** 判断点是否落在矩形内，边界也计入，可用于界面命中测试。 */
        public boolean contains(Point point) {
            return point.x() >= left && point.x() <= right && point.y() >= top && point.y() <= bottom;
        }

        /** 判断点是否严格位于矩形内部，边界不计入内部。 */
        public boolean containsInterior(Point point) {
            return point.x() > left && point.x() < right && point.y() > top && point.y() < bottom;
        }
    }

    private static final class Grid {

        private final double[] xs;
        private final double[] ys;
        private final Bounds[] obstacles;
        private final double[] distances;
        private final int[] parents;
        private final DoubleHeapIndirectPriorityQueue pending;

        private Grid(double[] xs, double[] ys, Bounds[] obstacles) {
            this.xs = xs;
            this.ys = ys;
            this.obstacles = obstacles;
            int nodeCount = Math.multiplyExact(xs.length, ys.length);
            distances = new double[nodeCount];
            Arrays.fill(distances, Double.POSITIVE_INFINITY);
            parents = new int[nodeCount];
            pending = new DoubleHeapIndirectPriorityQueue(distances, Math.min(xs.length, ys.length));
        }

        private ObjectList<Point> route(Point start, Point end) {
            int from = node(start), to = node(end);
            distances[from] = 0;
            parents[from] = -1;
            pending.enqueue(from);
            while (!pending.isEmpty()) {
                if (Thread.currentThread().isInterrupted()) throw new CancellationException();
                int current = pending.dequeue();
                if (current == to) return path(to);
                int x = current % xs.length, y = current / xs.length;
                if (x > 0) visit(current, x, y, x - 1, y);
                if (x + 1 < xs.length) visit(current, x, y, x + 1, y);
                if (y > 0) visit(current, x, y, x, y - 1);
                if (y + 1 < ys.length) visit(current, x, y, x, y + 1);
            }
            return ObjectList.of();
        }

        private int node(Point point) {
            return Arrays.binarySearch(ys, point.y()) * xs.length + Arrays.binarySearch(xs, point.x());
        }

        private void visit(int from, int x, int y, int tx, int ty) {
            double x0 = xs[x], y0 = ys[y], x1 = xs[tx], y1 = ys[ty];
            int node = ty * xs.length + tx;
            double length = distances[from] + Math.abs(x1 - x0) + Math.abs(y1 - y0);
            if (length >= distances[node]) return;
            for (Bounds obstacle : obstacles) {
                if (Math.max(x0, x1) > obstacle.left() && Math.min(x0, x1) < obstacle.right() && Math.max(y0, y1) > obstacle.top() && Math.min(y0, y1) < obstacle.bottom()) return;
            }
            distances[node] = length;
            parents[node] = from;
            if (pending.contains(node)) pending.changed(node);
            else pending.enqueue(node);
        }

        private ObjectList<Point> path(int node) {
            var path = new IntArrayList();
            for (int current = node; current >= 0; current = parents[current]) {
                int size = path.size();
                if (size > 1) {
                    int previous = path.getInt(size - 1), before = path.getInt(size - 2);
                    if (sameLine(before, previous, current)) {
                        path.set(size - 1, current);
                        continue;
                    }
                }
                path.add(current);
            }
            var points = new Point[path.size()];
            for (int i = 0; i < points.length; i++) {
                int index = path.getInt(points.length - 1 - i);
                points[i] = new Point(xs[index % xs.length], ys[index / xs.length]);
            }
            return ObjectList.of(points);
        }

        private boolean sameLine(int a, int b, int c) {
            int width = xs.length;
            return a % width == b % width && b % width == c % width || a / width == b / width && b / width == c / width;
        }
    }
}
