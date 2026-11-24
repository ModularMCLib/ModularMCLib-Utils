package com.modularmc.mmclutils.utils;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectList;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.concurrent.CancellationException;

/** 为节点布置层级，并在二维矩形之间计算可复用的水平及竖直折线。 */
public final class GraphUtils {

    private GraphUtils() {}

    /** 按边方向将有向无环图分层。节点编号须在零到 nodeCount 之间，含环的图应先提供其分组。 */
    public static ObjectList<IntList> layers(int nodeCount, Iterable<Edge> edges) {
        var outgoing = new IntArrayList[nodeCount];
        var pending = new IntArrayFIFOQueue();
        var degree = new int[nodeCount];
        var ranks = new int[nodeCount];
        for (Edge edge : edges) {
            int from = edge.from(), to = edge.to();
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

    /** 避开矩形内部的最短正交折线。允许沿边界经过。无路径时返回空列表。 */
    public static ObjectList<Point> route(Point start, Point end, Collection<Bounds> obstacles) {
        if (start.equals(end)) return ObjectList.of(start);
        int length = Math.addExact(2, Math.multiplyExact(obstacles.size(), 2));
        double[] xs = new double[length], ys = new double[length];
        xs[0] = start.x();
        xs[1] = end.x();
        ys[0] = start.y();
        ys[1] = end.y();
        int index = 2;
        for (Bounds obstacle : obstacles) {
            if (obstacle.containsInterior(start) || obstacle.containsInterior(end)) return ObjectList.of();
            xs[index] = obstacle.left();
            ys[index++] = obstacle.top();
            xs[index] = obstacle.right();
            ys[index++] = obstacle.bottom();
        }
        xs = Arrays.stream(xs).distinct().sorted().toArray();
        ys = Arrays.stream(ys).distinct().sorted().toArray();
        return new Grid(xs, ys, obstacles).route(start, end);
    }

    /** 连接源节点到目标节点的有向边，节点用从零开始的整数下标表示。 */
    public record Edge(int from, int to) {}

    /** 二维平面中的点。坐标须为有限值。 */
    public record Point(double x, double y) {

        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("折线坐标须为有限值");
        }
    }

    /** 平面上的闭合矩形。坐标须为有限值且左上角不超过右下角。 */
    public record Bounds(double left, double top, double right, double bottom) {

        public Bounds {
            if (!(left < right && top < bottom) || !Double.isFinite(left) || !Double.isFinite(top) || !Double.isFinite(right) || !Double.isFinite(bottom)) {
                throw new IllegalArgumentException("折线路由的阻挡矩形无效");
            }
        }

        /** 判断点是否严格位于矩形内部，边界不计入内部。 */
        public boolean containsInterior(Point point) {
            return point.x() > left && point.x() < right && point.y() > top && point.y() < bottom;
        }
    }

    private record Step(int index, double length) {}

    private static final class Grid {

        private final double[] xs;
        private final double[] ys;
        private final Collection<Bounds> obstacles;
        private final double[] distances;
        private final Int2IntOpenHashMap parents = new Int2IntOpenHashMap();
        private final ObjectHeapPriorityQueue<Step> pending = new ObjectHeapPriorityQueue<>(
                Comparator.comparingDouble(Step::length).thenComparingInt(Step::index));

        private Grid(double[] xs, double[] ys, Collection<Bounds> obstacles) {
            this.xs = xs;
            this.ys = ys;
            this.obstacles = obstacles;
            distances = new double[Math.multiplyExact(xs.length, ys.length)];
            Arrays.fill(distances, Double.POSITIVE_INFINITY);
            parents.defaultReturnValue(-1);
        }

        private ObjectList<Point> route(Point start, Point end) {
            int from = node(start), to = node(end);
            distances[from] = 0;
            pending.enqueue(new Step(from, 0));
            while (!pending.isEmpty()) {
                if (Thread.currentThread().isInterrupted()) throw new CancellationException();
                Step step = pending.dequeue();
                if (step.length() != distances[step.index()]) continue;
                if (step.index() == to) return path(to);
                int x = step.index() % xs.length, y = step.index() / xs.length;
                if (x > 0) visit(step, x, y, x - 1, y);
                if (x + 1 < xs.length) visit(step, x, y, x + 1, y);
                if (y > 0) visit(step, x, y, x, y - 1);
                if (y + 1 < ys.length) visit(step, x, y, x, y + 1);
            }
            return ObjectList.of();
        }

        private int node(Point point) {
            return Arrays.binarySearch(ys, point.y()) * xs.length + Arrays.binarySearch(xs, point.x());
        }

        private void visit(Step from, int x, int y, int tx, int ty) {
            double x0 = xs[x], y0 = ys[y], x1 = xs[tx], y1 = ys[ty];
            int node = ty * xs.length + tx;
            double length = from.length() + Math.abs(x1 - x0) + Math.abs(y1 - y0);
            if (length >= distances[node]) return;
            for (Bounds obstacle : obstacles) {
                if (Math.max(x0, x1) > obstacle.left() && Math.min(x0, x1) < obstacle.right() && Math.max(y0, y1) > obstacle.top() && Math.min(y0, y1) < obstacle.bottom()) return;
            }
            distances[node] = length;
            parents.put(node, from.index());
            pending.enqueue(new Step(node, length));
        }

        private ObjectList<Point> path(int node) {
            var path = new ObjectArrayList<Point>();
            while (node >= 0) {
                var point = new Point(xs[node % xs.length], ys[node / xs.length]);
                int size = path.size();
                if (size > 1) {
                    Point previous = path.get(size - 1), before = path.get(size - 2);
                    if ((before.x() == previous.x() && previous.x() == point.x()) || (before.y() == previous.y() && previous.y() == point.y())) path.removeLast();
                }
                path.add(point);
                node = parents.get(node);
            }
            return ObjectList.of(path.reversed().toArray(Point[]::new));
        }
    }
}
