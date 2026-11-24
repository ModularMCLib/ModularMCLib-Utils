package com.modularmc.mmclutils.client.model;

import net.minecraft.client.renderer.block.model.Variant;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import com.mojang.math.Transformation;
import lombok.Getter;
import org.joml.Quaternionf;

/**
 * 记录可定向方块的 24 种正交旋转。每个枚举值的前缀是世界前向，后缀是用水平方向编码的滚转。
 * <p>
 * 旋转表源自
 * <a href=
 * "https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/forge/1.20.1/src/main/java/appeng/api/orientation/BlockOrientation.java">Applied
 * Energistics 2</a>，采用 LGPL 3.0 许可。
 */
public enum BlockModelRotation {

    // 前向按原版三维方向编号排列，每组内部按原版二维方向编号排列。
    DOWN_SOUTH(90, 0, 180),
    DOWN_WEST(90, 0, 270),
    DOWN_NORTH(90, 0, 0),
    DOWN_EAST(90, 0, 90),

    UP_SOUTH(270, 0, 0),
    UP_WEST(270, 0, 270),
    UP_NORTH(270, 0, 180),
    UP_EAST(270, 0, 90),

    NORTH_SOUTH(0, 0, 180),
    NORTH_WEST(0, 0, 90),
    NORTH_NORTH(0, 0, 0),
    NORTH_EAST(0, 0, 270),

    SOUTH_SOUTH(0, 180, 180),
    SOUTH_WEST(0, 180, 90),
    SOUTH_NORTH(0, 180, 0),
    SOUTH_EAST(0, 180, 270),

    WEST_SOUTH(0, 270, 180),
    WEST_WEST(0, 270, 90),
    WEST_NORTH(0, 270, 0),
    WEST_EAST(0, 270, 270),

    EAST_SOUTH(0, 90, 180),
    EAST_WEST(0, 90, 90),
    EAST_NORTH(0, 90, 0),
    EAST_EAST(0, 90, 270);

    /** 按原版方向编号和水平旋转编号索引的旋转表。调用方应将数组视为只读。 */
    public static final BlockModelRotation[] VALUES = values();

    /** 模型绕 X 轴旋转的角度，以度为单位。 */
    @Getter
    private final int angleX;
    /** 模型绕 Y 轴旋转的角度，以度为单位。 */
    @Getter
    private final int angleY;
    /** 模型绕 Z 轴旋转的角度，以度为单位。 */
    @Getter
    private final int angleZ;

    BlockModelRotation(int angleX, int angleY, int angleZ) {
        this.angleX = angleX;
        this.angleY = angleY;
        this.angleZ = angleZ;
    }

    /**
     * 按世界前向与滚转编码查找方块的旋转参数。
     *
     * @param frontFacing   模型局部前方朝向的世界面
     * @param upwardsFacing 原版水平旋转编码，须为水平方向
     * @return 对应的 X、Y、Z 旋转角
     */
    public static BlockModelRotation get(Direction frontFacing, Direction upwardsFacing) {
        return VALUES[frontFacing.get3DDataValue() * 4 + upwardsFacing.get2DDataValue()];
    }

    /** 返回此朝向的原版坐标变换。 */
    public Transformation transformation() {
        return transformation(angleX, angleY, angleZ);
    }

    /**
     * 应用此朝向，保留变体的模型位置和 UV 锁定设置。
     *
     * @param variant 原版模型变体
     * @return 旋转后的模型变体
     */
    public Variant rotate(Variant variant) {
        return rotate(variant, angleX, angleY, angleZ);
    }

    /**
     * 返回正交模型变换。先应用模型局部 Z 轴滚转，再依次应用 X 轴俯仰与 Y 轴偏航。
     * 各角度使用原版方块模型的方向约定。
     *
     * @param x 绕 X 轴的角度
     * @param y 绕 Y 轴的角度
     * @param z 绕 Z 轴的角度
     * @return 与给定角度对应的原版变换
     * @throws IllegalArgumentException 任一轴的角度不是 0、90、180 或 270 度
     */
    public static Transformation transformation(int x, int y, int z) {
        checkAngle(x);
        checkAngle(y);
        checkAngle(z);
        var rotation = new Quaternionf().rotateY(-y * Mth.DEG_TO_RAD).rotateX(-x * Mth.DEG_TO_RAD).rotateZ(-z * Mth.DEG_TO_RAD);
        return new Transformation(null, rotation, null, null);
    }

    /**
     * 以指定变换替换变体的旋转，保留模型位置与 UV 锁定。变换顺序与 {@link #transformation(int, int, int)} 一致。
     *
     * @param variant 原版模型变体
     * @param x       绕 X 轴的角度
     * @param y       绕 Y 轴的角度
     * @param z       绕 Z 轴的角度
     * @return 旋转后的模型变体
     * @throws IllegalArgumentException 任一轴的角度不是 0、90、180 或 270 度
     */
    public static Variant rotate(Variant variant, int x, int y, int z) {
        return new Variant(variant.getModelLocation(), transformation(x, y, z), variant.isUvLocked(), variant.getWeight());
    }

    private static void checkAngle(int angle) {
        if (angle != 0 && angle != 90 && angle != 180 && angle != 270) {
            throw new IllegalArgumentException("模型旋转角度无效 " + angle);
        }
    }
}
