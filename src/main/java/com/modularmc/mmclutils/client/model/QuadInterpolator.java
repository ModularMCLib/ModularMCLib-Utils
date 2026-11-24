/*
 * This file is part of CodeChickenLib.
 * Copyright (c) 2018, covers1624, All rights reserved.
 *
 * CodeChickenLib is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * CodeChickenLib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with CodeChickenLib. If not, see <http://www.gnu.org/licenses/lgpl>.
 */
package com.modularmc.mmclutils.client.model;

import net.minecraft.util.Mth;

/**
 * 在矩形的四个角之间做双线性插值。依次调用 {@link #reset} 和 {@link #setup} 设定角点，
 * 再用 {@link #locate} 选定位置。同一位置可用于连续插值 UV 和颜色，各线程须各自持有实例。
 */
public class QuadInterpolator {

    private final float[][] posCache = new float[4][2];
    private final float[] valCache = new float[4];

    private float x0;
    private float x1;
    private float y0;
    private float y1;

    private float rX;
    private float rY;

    private int p00;
    private int p10;
    private int p11;
    private int p01;

    /**
     * 用沿两轴对齐的四边形重置插值数据。以第一个顶点为原点，其余顶点可按任意顺序传入。
     */
    public void reset(float dx0, float dy0, float dx1, float dy1, float dx2, float dy2, float dx3, float dy3) {
        float[] vec0 = this.posCache[0];
        float[] vec1 = this.posCache[1];
        float[] vec2 = this.posCache[2];
        float[] vec3 = this.posCache[3];

        vec0[0] = dx0;
        vec1[0] = dx1;
        vec2[0] = dx2;
        vec3[0] = dx3;

        vec0[1] = dy0;
        vec1[1] = dy1;
        vec2[1] = dy2;
        vec3[1] = dy3;
    }

    /**
     * 在开始插值前结束顶点坐标采集。
     */
    public void setup() {
        this.p00 = 0; // 第一个顶点为插值原点。
        this.x0 = this.posCache[this.p00][0];
        this.y0 = this.posCache[this.p00][1];

        for (int i = 1; i < 4; i++) {
            float x = this.posCache[i][0];
            float y = this.posCache[i][1];

            if (this.y0 == y) {
                // 右下。
                this.p10 = i;
                this.x1 = x;
            } else if (this.x0 == x) {
                // 左上。
                this.p01 = i;
                this.y1 = y;
            } else {
                // 右上。
                this.p11 = i;
            }
        }
    }

    /**
     * 按给定点计算双线性插值权重。
     *
     * @param x 插值点的横坐标
     * @param y 插值点的纵坐标
     */
    public void locate(float x, float y) {
        this.rX = Mth.inverseLerp(x, this.x0, this.x1);
        this.rY = Mth.inverseLerp(y, this.y0, this.y1);
    }

    /**
     * 使用已计算的系数插值。
     *
     * @param q0 第一个顶点的数值
     * @param q1 第二个顶点的数值
     * @param q2 第三个顶点的数值
     * @param q3 第四个顶点的数值
     * @return 插值结果
     */
    public float interpolate(float q0, float q1, float q2, float q3) {
        this.valCache[0] = q0;
        this.valCache[1] = q1;
        this.valCache[2] = q2;
        this.valCache[3] = q3;
        float f0 = Mth.lerp(this.rX, this.valCache[this.p00], this.valCache[this.p10]);
        float f1 = Mth.lerp(this.rX, this.valCache[this.p01], this.valCache[this.p11]);

        return Mth.lerp(this.rY, f0, f1);
    }
}
