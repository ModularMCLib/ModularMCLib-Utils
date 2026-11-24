package com.modularmc.mmclutils.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.LongComparator;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Locale;

/** 将局部方向、点击位置与打包坐标映射到原版方块面。 */
public final class DirectionUtils {

    private static final Direction[] DIRECTIONS = Direction.values();
    /** 六个原版方向的低位掩码，可用于限定外部提供的方向位。 */
    public static final int ALL_SIDES = (1 << DIRECTIONS.length) - 1;

    private DirectionUtils() {}

    /** 将原版打包方块位置沿给定朝向移动一格。 */
    public static long offset(long packedPosition, Direction side) {
        return BlockPos.offset(packedPosition, side);
    }

    /** 以方块中心为起点，用指向世界坐标的向量选择最近世界方向。 */
    public static Direction nearestSide(Vec3 point, BlockPos position) {
        return Direction.getNearest(point.x - position.getX() - .5,
                point.y - position.getY() - .5, point.z - position.getZ() - .5);
    }

    /** 按原版水平序号旋转四分之一圈的整数倍。垂直方向返回原值。 */
    public static Direction rotateHorizontal(Direction direction, int quarterTurns) {
        if (direction.getAxis().isVertical()) return direction;
        return Direction.from2DDataValue((direction.get2DDataValue() + quarterTurns) & 3);
    }

    /** 中央返回命中面，边缘返回相邻面，四角返回背面。 */
    public static Direction gridSide(BlockHitResult hit) {
        Vec3 point = hit.getLocation();
        BlockPos pos = hit.getBlockPos();
        return gridSide(hit.getDirection(), point.x - pos.getX(), point.y - pos.getY(), point.z - pos.getZ());
    }

    /** 坐标相对命中的方块，边缘带宽各占四分之一。 */
    public static Direction gridSide(Direction face, double x, double y, double z) {
        return switch (face.getAxis()) {
            case X -> gridSide(face, edge(z, Direction.NORTH, Direction.SOUTH), edge(y, Direction.DOWN, Direction.UP));
            case Y -> gridSide(face, edge(x, Direction.WEST, Direction.EAST), edge(z, Direction.NORTH, Direction.SOUTH));
            case Z -> gridSide(face, edge(x, Direction.WEST, Direction.EAST), edge(y, Direction.DOWN, Direction.UP));
        };
    }

    private static @Nullable Direction edge(double coordinate, Direction low, Direction high) {
        if (coordinate < .25) return low;
        return coordinate > .75 ? high : null;
    }

    private static Direction gridSide(Direction face, @Nullable Direction first, @Nullable Direction second) {
        if (first != null) return second == null ? first : face.getOpposite();
        return second == null ? face : second;
    }

    /** 仅在两个位置共享一个方块面时返回从源指向邻居的方向。 */
    public static @Nullable Direction neighborSide(long source, long neighbor) {
        int x = BlockPos.getX(neighbor) - BlockPos.getX(source);
        int y = BlockPos.getY(neighbor) - BlockPos.getY(source);
        int z = BlockPos.getZ(neighbor) - BlockPos.getZ(source);
        return Math.abs(x) + Math.abs(y) + Math.abs(z) == 1 ? Direction.getNearest(x, y, z) : null;
    }

    /** 取得两个接壤方块之间的方向。两个位置重合或不共享方块面时返回空值。 */
    public static @Nullable Direction neighborSide(BlockPos source, BlockPos neighbor) {
        return neighborSide(source.asLong(), neighbor.asLong());
    }

    /** 每个方向占原版方向索引对应的一位，可直接用于比较区段的可见面。 */
    public static int mask(Direction side) {
        return 1 << side.ordinal();
    }

    /** 将给定方向合并成一个掩码，重复方向只占一位。 */
    public static int mask(Iterable<Direction> sides) {
        int mask = 0;
        for (Direction side : sides) mask |= mask(side);
        return mask;
    }

    /** 从低位方向掩码创建枚举集合。超出原版方向数量的高位被忽略。 */
    public static EnumSet<Direction> sides(int mask) {
        var result = EnumSet.noneOf(Direction.class);
        replaceSides(result, mask);
        return result;
    }

    /** 忽略高于原版方向数的位，返回集合是否变化。 */
    public static boolean replaceSides(EnumSet<Direction> target, int mask) {
        mask &= ALL_SIDES;
        if (mask(target) == mask) return false;
        target.clear();
        while (mask != 0) {
            target.add(DIRECTIONS[Integer.numberOfTrailingZeros(mask)]);
            mask &= mask - 1;
        }
        return true;
    }

    /** 以北为前时的六个局部面。 */
    public enum LocalSide implements StringRepresentable {

        /** 前方，正对局部朝向。 */
        FRONT(Direction.NORTH),
        /** 后方，与前方相反。 */
        BACK(Direction.SOUTH),
        /** 上方，沿局部上轴。 */
        UP(Direction.UP),
        /** 下方，与局部上方向相反。 */
        DOWN(Direction.DOWN),
        /** 从后朝前观察时的左侧。 */
        LEFT(Direction.WEST),
        /** 从后朝前观察时的右侧。 */
        RIGHT(Direction.EAST);

        /** 通过小写英文序列化名称保存局部面。 */
        public static final EnumCodec<LocalSide> CODEC = StringRepresentable.fromEnum(LocalSide::values);
        private static final LocalSide[] BY_SIDE = new LocalSide[DIRECTIONS.length];

        static {
            for (LocalSide local : values()) BY_SIDE[local.side.ordinal()] = local;
        }

        private final Direction side;
        private final String name;

        LocalSide(Direction side) {
            this.side = side;
            this.name = name().toLowerCase(Locale.ROOT);
        }

        /** 在以北为前的朝向中，把世界方向转换为局部面。 */
        public static LocalSide fromNorthFacing(Direction side) {
            return BY_SIDE[side.ordinal()];
        }

        /** 取得以北为前、以世界上方为上的默认朝向中的世界方向。 */
        public Direction northFacing() {
            return side;
        }

        /** 取得同轴相反的局部面。 */
        public LocalSide opposite() {
            return fromNorthFacing(side.getOpposite());
        }

        /** 检查两个局部面是否沿同一条轴，包括相对的面。 */
        public boolean isSameAxis(LocalSide other) {
            return side.getAxis() == other.side.getAxis();
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** 前方与上方使用互相垂直的世界方向。镜像沿左右轴生效。 */
    public record Orientation(Direction front, Direction up, boolean mirrored) {

        /** 创建正交朝向。前方和上方沿同轴时抛出 {@link IllegalArgumentException}。 */
        public Orientation {
            if (front.getAxis() == up.getAxis()) throw new IllegalArgumentException("前方和上方必须互相垂直");
        }

        /** 由前方构造默认朝向。前方为垂直方向时取世界北方作为局部上方。 */
        public static Orientation facing(Direction front) {
            return new Orientation(front, front.getAxis().isVertical() ? Direction.NORTH : Direction.UP, false);
        }

        /** 指向水平时，北、南、西、东分别表示正立、倒置、向右和向左翻转九十度。 */
        public static Orientation fromRoll(Direction front, Direction roll, boolean mirrored) {
            Direction up = front.getAxis().isVertical() ? roll : switch (roll) {
                case NORTH -> Direction.UP;
                case SOUTH -> Direction.DOWN;
                case EAST -> front.getCounterClockWise();
                case WEST -> front.getClockWise();
                default -> throw new IllegalArgumentException("旋转编码必须使用水平方向");
            };
            return new Orientation(front, up, mirrored);
        }

        /** 返回局部左侧在世界坐标中的方向，镜像时交换左右。 */
        public Direction left() {
            Direction side = Direction.getNearest(
                    up.getStepY() * front.getStepZ() - up.getStepZ() * front.getStepY(),
                    up.getStepZ() * front.getStepX() - up.getStepX() * front.getStepZ(),
                    up.getStepX() * front.getStepY() - up.getStepY() * front.getStepX());
            return mirrored ? side.getOpposite() : side;
        }

        /** 把局部面转换为当前朝向中的世界方向。 */
        public Direction direction(LocalSide side) {
            return switch (side) {
                case FRONT -> front;
                case BACK -> front.getOpposite();
                case UP -> up;
                case DOWN -> up.getOpposite();
                case LEFT -> left();
                case RIGHT -> left().getOpposite();
            };
        }

        /** 把世界方向转换为当前朝向中的局部面。 */
        public LocalSide localSide(Direction side) {
            if (side.getAxis() == front.getAxis()) return side == front ? LocalSide.FRONT : LocalSide.BACK;
            if (side.getAxis() == up.getAxis()) return side == up ? LocalSide.UP : LocalSide.DOWN;
            return side == left() ? LocalSide.LEFT : LocalSide.RIGHT;
        }

        /** 取得可传回 {@link #fromRoll} 的翻转编码。水平前方使用四个水平方向表示四种转角。 */
        public Direction roll() {
            if (front.getAxis().isVertical()) return up;
            if (up == Direction.UP) return Direction.NORTH;
            if (up == Direction.DOWN) return Direction.SOUTH;
            return up == front.getCounterClockWise() ? Direction.EAST : Direction.WEST;
        }

        /** 保留翻转状态，将坐标轴一起旋到新的前方。 */
        public Orientation withFront(Direction nextFront) {
            if (front == nextFront) return this;
            Direction nextUp;
            if (front.getAxis().isHorizontal() && nextFront.getAxis().isHorizontal()) {
                nextUp = rotateHorizontal(up, nextFront.get2DDataValue() - front.get2DDataValue());
            } else if (front.getAxis().isVertical() && nextFront.getAxis().isVertical()) {
                nextUp = up.getOpposite();
            } else if (up.getAxis() == nextFront.getAxis()) {
                nextUp = up == nextFront ? front.getOpposite() : front;
            } else {
                nextUp = up;
            }
            return new Orientation(nextFront, nextUp, mirrored);
        }

        /** 以局部坐标的左、上、前方向移动给定步数，返回打包方块位置。 */
        public long offset(long position, int leftSteps, int upSteps, int forwardSteps) {
            Direction left = left();
            return BlockPos.offset(position,
                    left.getStepX() * leftSteps + up.getStepX() * upSteps + front.getStepX() * forwardSteps,
                    left.getStepY() * leftSteps + up.getStepY() * upSteps + front.getStepY() * forwardSteps,
                    left.getStepZ() * leftSteps + up.getStepZ() * upSteps + front.getStepZ() * forwardSteps);
        }

        /** 以局部坐标的左、上、前方向移动给定步数，返回原版位置对象。 */
        public BlockPos offset(BlockPos position, int leftSteps, int upSteps, int forwardSteps) {
            return BlockPos.of(offset(position.asLong(), leftSteps, upSteps, forwardSteps));
        }

        /** 按指定局部朝向投影并比较打包坐标。垂直于该方向的坐标差不会影响顺序。 */
        public LongComparator packedOrder(LocalSide side) {
            Direction direction = direction(side);
            return switch (direction) {
                case EAST -> (a, b) -> Integer.compare(BlockPos.getX(a), BlockPos.getX(b));
                case WEST -> (a, b) -> Integer.compare(BlockPos.getX(b), BlockPos.getX(a));
                case UP -> (a, b) -> Integer.compare(BlockPos.getY(a), BlockPos.getY(b));
                case DOWN -> (a, b) -> Integer.compare(BlockPos.getY(b), BlockPos.getY(a));
                case SOUTH -> (a, b) -> Integer.compare(BlockPos.getZ(a), BlockPos.getZ(b));
                case NORTH -> (a, b) -> Integer.compare(BlockPos.getZ(b), BlockPos.getZ(a));
            };
        }

        /** 按指定局部朝向投影并比较原版方块位置。 */
        public Comparator<BlockPos> order(LocalSide side) {
            Direction direction = direction(side);
            Direction.Axis axis = direction.getAxis();
            Comparator<BlockPos> order = Comparator.comparingInt(pos -> axis.choose(pos.getX(), pos.getY(), pos.getZ()));
            return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? order : order.reversed();
        }
    }

    /** 按 0 至 16 的方块模型坐标计算面外侧的包围盒。方向为空时取中央立方体。 */
    public static Pair<Vector3f, Vector3f> sideBounds(@Nullable Direction dir, float min, float max) {
        float x1 = min, y1 = min, z1 = min, x2 = max, y2 = max, z2 = max;
        if (dir != null) {
            switch (dir) {
                case DOWN -> {
                    y1 = 0;
                    y2 = min;
                }
                case UP -> {
                    y1 = max;
                    y2 = 16;
                }
                case NORTH -> {
                    z1 = 0;
                    z2 = min;
                }
                case SOUTH -> {
                    z1 = max;
                    z2 = 16;
                }
                case WEST -> {
                    x1 = 0;
                    x2 = min;
                }
                case EAST -> {
                    x1 = max;
                    x2 = 16;
                }
            }
        }
        return Pair.of(new Vector3f(x1, y1, z1), new Vector3f(x2, y2, z2));
    }
}
