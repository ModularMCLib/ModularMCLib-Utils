package com.modularmc.mmclutils.spatial;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import it.unimi.dsi.fastutil.longs.LongComparator;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Locale;

/** 将局部方向、点击位置与打包坐标映射到原版方块面。 */
public final class BlockDirections {

    private static final Direction[] DIRECTIONS = Direction.values();
    public static final int ALL_SIDES = (1 << DIRECTIONS.length) - 1;

    private BlockDirections() {}

    public static long offset(long packedPosition, Direction side) {
        return BlockPos.offset(packedPosition, side);
    }

    public static Direction nearestSide(Vec3 point, BlockPos position) {
        return Direction.getApproximateNearest(point.x - position.getX() - .5,
                point.y - position.getY() - .5, point.z - position.getZ() - .5);
    }

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
        return Math.abs(x) + Math.abs(y) + Math.abs(z) == 1 ? Direction.getNearest(x, y, z, null) : null;
    }

    public static @Nullable Direction neighborSide(BlockPos source, BlockPos neighbor) {
        return neighborSide(source.asLong(), neighbor.asLong());
    }

    /** 每个方向占原版方向索引对应的一位，可直接用于比较区段的可见面。 */
    public static int mask(Direction side) {
        return 1 << side.ordinal();
    }

    public static int mask(Iterable<Direction> sides) {
        int mask = 0;
        for (Direction side : sides) mask |= mask(side);
        return mask;
    }

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

        FRONT(Direction.NORTH),
        BACK(Direction.SOUTH),
        UP(Direction.UP),
        DOWN(Direction.DOWN),
        LEFT(Direction.WEST),
        RIGHT(Direction.EAST);

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

        public static LocalSide fromNorthFacing(Direction side) {
            return BY_SIDE[side.ordinal()];
        }

        public Direction northFacing() {
            return side;
        }

        public LocalSide opposite() {
            return fromNorthFacing(side.getOpposite());
        }

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

        public Orientation {
            if (front.getAxis() == up.getAxis()) throw new IllegalArgumentException("前方和上方必须互相垂直");
        }

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

        public Direction left() {
            Direction side = Direction.getApproximateNearest(
                    up.getStepY() * front.getStepZ() - up.getStepZ() * front.getStepY(),
                    up.getStepZ() * front.getStepX() - up.getStepX() * front.getStepZ(),
                    up.getStepX() * front.getStepY() - up.getStepY() * front.getStepX());
            return mirrored ? side.getOpposite() : side;
        }

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

        public LocalSide localSide(Direction side) {
            if (side.getAxis() == front.getAxis()) return side == front ? LocalSide.FRONT : LocalSide.BACK;
            if (side.getAxis() == up.getAxis()) return side == up ? LocalSide.UP : LocalSide.DOWN;
            return side == left() ? LocalSide.LEFT : LocalSide.RIGHT;
        }

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

        public long offset(long position, int leftSteps, int upSteps, int forwardSteps) {
            Direction left = left();
            return BlockPos.offset(position,
                    left.getStepX() * leftSteps + up.getStepX() * upSteps + front.getStepX() * forwardSteps,
                    left.getStepY() * leftSteps + up.getStepY() * upSteps + front.getStepY() * forwardSteps,
                    left.getStepZ() * leftSteps + up.getStepZ() * upSteps + front.getStepZ() * forwardSteps);
        }

        public BlockPos offset(BlockPos position, int leftSteps, int upSteps, int forwardSteps) {
            return BlockPos.of(offset(position.asLong(), leftSteps, upSteps, forwardSteps));
        }

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

        public Comparator<BlockPos> order(LocalSide side) {
            Direction direction = direction(side);
            Direction.Axis axis = direction.getAxis();
            Comparator<BlockPos> order = Comparator.comparingInt(pos -> axis.choose(pos.getX(), pos.getY(), pos.getZ()));
            return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? order : order.reversed();
        }
    }
}
