package com.modularmc.mmclutils.utils;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Tables;
import org.jetbrains.annotations.Contract;
import org.joml.*;

import java.lang.Math;
import java.security.InvalidParameterException;
import java.text.NumberFormat;
import java.util.EnumMap;
import java.util.Map;

public class MatrixUtils {

    private static final Map<Direction, Vector3fc> directionAxes = Util.make(new EnumMap<>(Direction.class), map -> {
        for (Direction dir : Direction.values()) {
            map.put(dir, dir.step());
        }
    });
    private static final Table<Direction, Direction, Matrix4fc> rotations = Tables
            .synchronizedTable(HashBasedTable.create());

    /**
     * @param from 原始向量
     * @param to   目标向量
     * @return 将原始向量转向目标所需的角度
     */
    @Contract(pure = true)
    public static float getRotationAngle(final Vector3fc from, final Vector3fc to) {
        return (float) Math.acos(from.dot(to));
    }

    /**
     * 原地修改 {@code from}，将它用于存放计算出的旋转轴。
     *
     * @param from 原始向量
     * @param to   目标向量
     * @return 将原始向量转向目标所需的旋转轴
     */
    public static Vector3f getRotationAxis(Vector3f from, final Vector3fc to) {
        return getRotationAxis(from, to, from);
    }

    /**
     * @param from 原始向量
     * @param to   目标向量
     * @param dest 接收结果的向量
     * @return {@code dest}
     */
    public static Vector3f getRotationAxis(final Vector3fc from, final Vector3fc to, Vector3f dest) {
        return from.cross(to, dest).normalize();
    }

    /**
     * @param from 原始向量
     * @param to   目标向量
     * @return 将原始向量转向目标的四元数
     */
    @Contract(pure = true)
    public static Quaternionf getRotation(final Vector3fc from, final Vector3fc to) {
        return from.rotationTo(to, new Quaternionf());
    }

    /**
     * @param from 原始方向
     * @param to   目标方向
     * @return 将原始方向转向目标的四元数
     */
    @Contract(pure = true)
    public static Quaternionf getRotation(final Direction from, final Direction to) {
        return getRotation(getDirectionAxis(from), getDirectionAxis(to));
    }

    /**
     * 旋转矩阵及所有附加向量，使 {@code from} 转向 {@code to}。
     *
     * @param matrix     原地修改的矩阵
     * @param from       原始向量
     * @param to         目标向量
     * @param additional 应同步旋转的向量
     */
    public static void rotateMatrix(Matrix4f matrix, Vector3f from, Vector3fc to, Vector3f... additional) {
        if (from.equals(to)) {
            return;
        }
        if (-from.x() == to.x() && -from.y() == to.y() && -from.z() == to.z()) {
            rotateMatrix(matrix, Mth.PI, getDirectionAxis(Direction.UP), additional);
        } else {
            var angle = getRotationAngle(from, to);
            getRotationAxis(from, to);
            rotateMatrix(matrix, angle, from, additional);
        }
    }

    /**
     * @param matrix     原地修改的矩阵
     * @param angle      旋转角度，单位为弧度
     * @param axis       旋转轴
     * @param additional 应同步旋转的向量
     */
    public static void rotateMatrix(Matrix4f matrix, float angle, Vector3fc axis, Vector3f... additional) {
        matrix.rotate(angle, axis);
        for (var vec : additional) {
            vec.rotateAxis(angle, axis.x(), axis.y(), axis.z());
        }
    }

    /**
     * @param upward 局部上方对应的方向
     * @return 以前面法线为轴的旋转角度，单位为弧度
     */
    public static float upwardFacingAngle(Direction upward) {
        return switch (upward) {
            case NORTH -> 0;
            case SOUTH -> 2;
            case WEST -> 3;
            case EAST -> 1;
            default -> throw new InvalidParameterException("Upward facing can't be up/down");
        } * Mth.HALF_PI;
    }

    public static Vector3f rotateMatrixToFront(Matrix4f matrix, Direction frontFace) {
        // 将局部前方旋到指定世界方向。
        var front = frontFace.step();
        rotateMatrix(matrix, Direction.NORTH.step(), getDirectionAxis(frontFace), front);
        return front;
    }

    public static void rotateMatrixToUp(Matrix4f matrix, Vector3f front, Direction upwardsFace) {
        // 围绕前方轴调整局部上方。
        rotateMatrix(matrix, upwardFacingAngle(upwardsFace), front);
    }

    public static Matrix4fc createRotationState(Direction frontFace, Direction upwardFace) {
        if (rotations.contains(frontFace, upwardFace)) {
            var rotation = rotations.get(frontFace, upwardFace);
            assert rotation != null;
            return rotation;
        }
        var matrix = new Matrix4f();
        var front = rotateMatrixToFront(matrix, frontFace);
        front.absolute();
        rotateMatrixToUp(matrix, front, upwardFace);
        rotations.put(frontFace, upwardFace, matrix);
        return matrix;
    }

    public static Vector3fc getDirectionAxis(Direction dir) {
        return directionAxes.get(dir);
    }

    /** 按 JOML 的数字格式将矩阵的四行放在同一行。 */
    public static String formatRows(Matrix4fc matrix) {
        return formatRows(matrix, (NumberFormat) Options.NUMBER_FORMAT.clone());
    }

    /** 四行均使用调用方指定的数字格式。 */
    public static String formatRows(Matrix4fc matrix, NumberFormat format) {
        StringBuilder result = new StringBuilder("{");
        Vector4f row = new Vector4f();
        for (int index = 0; index < 4; index++) {
            if (index != 0) result.append(", ");
            matrix.getRow(index, row);
            result.append('[').append(format.format(row.x)).append(' ')
                    .append(format.format(row.y)).append(' ')
                    .append(format.format(row.z)).append(' ')
                    .append(format.format(row.w)).append(']');
        }
        return result.append('}').toString();
    }
}
