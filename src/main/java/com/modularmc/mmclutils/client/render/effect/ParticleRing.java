package com.modularmc.mmclutils.client.render.effect;

import org.joml.Matrix3f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.SplittableRandom;

/**
 * 固定随机种子的环粒子数据，可用同一个实例在多个位置绘制。
 * 实例保存粒子运动参数，渲染器负责缓存并释放上传到设备的数据。
 */
public final class ParticleRing {

    /** 基础粒子边长，单位为方块。 */
    public static final float PIXEL = 1F / 16F;
    /** 变换反馈路径中粒子数据纹理每行的像素数。 */
    public static final int DATA_WIDTH = 1024;

    private final float majorRadius;
    private final float half;
    private final int count;
    private final float[] phase;
    private final float[] orbit;
    private final float[] driftFrequencyU;
    private final float[] driftFrequencyV;
    private final float[] driftPhaseU;
    private final float[] driftPhaseV;
    private final float[] spin;
    private final float[] spinPhase;
    private final float[] amplitudeU;
    private final float[] amplitudeV;
    private final float[] axisX;
    private final float[] axisY;
    private final float[] axisZ;
    private final int[] tone;

    /**
     * 根据固定种子分布粒子。粒子运动参数生成后可重复使用。
     *
     * @param majorRadius 环中心到轨道中线的半径，须为有限正数
     * @param tubeRadius  轨道横截面半径，须为有限非负数
     * @param count       粒子数。零表示只绘制光带
     * @param minSpeed    最小轨道线速度，单位为方块每 Tick
     * @param maxSpeed    最大轨道线速度，单位为方块每 Tick
     * @param seed        控制分布的随机种子
     * @throws IllegalArgumentException 半径、粒子数或速度范围无效
     */
    public ParticleRing(float majorRadius, float tubeRadius, int count, float minSpeed, float maxSpeed, long seed) {
        if (!(majorRadius > 0 && Float.isFinite(majorRadius) && tubeRadius >= 0 && Float.isFinite(tubeRadius))) {
            throw new IllegalArgumentException("环的半径超出有效范围");
        }
        if (count < 0 || !Float.isFinite(minSpeed) || !Float.isFinite(maxSpeed) || minSpeed > maxSpeed) {
            throw new IllegalArgumentException("环的粒子数或速度范围无效");
        }
        this.majorRadius = majorRadius;
        this.half = PIXEL / 2;
        this.count = count;
        phase = new float[count];
        orbit = new float[count];
        driftFrequencyU = new float[count];
        driftFrequencyV = new float[count];
        driftPhaseU = new float[count];
        driftPhaseV = new float[count];
        spin = new float[count];
        spinPhase = new float[count];
        amplitudeU = new float[count];
        amplitudeV = new float[count];
        axisX = new float[count];
        axisY = new float[count];
        axisZ = new float[count];
        tone = new int[count];
        var random = new SplittableRandom(seed);
        int[] order = new int[count];
        for (int i = 0; i < count; i++) order[i] = i;
        for (int i = count - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = order[i];
            order[i] = order[j];
            order[j] = swap;
        }
        float reach = Math.max(0, tubeRadius - half * 1.8F);
        for (int k = 0; k < count; k++) {
            int i = order[k];
            phase[i] = (float) ((k + random.nextDouble()) / count * Math.PI * 2);
            orbit[i] = (float) ((minSpeed + (maxSpeed - minSpeed) * random.nextDouble()) / majorRadius);
            driftFrequencyU[i] = (float) (0.03 + 0.07 * random.nextDouble());
            driftFrequencyV[i] = (float) (0.03 + 0.07 * random.nextDouble());
            driftPhaseU[i] = (float) (random.nextDouble() * Math.PI * 2);
            driftPhaseV[i] = (float) (random.nextDouble() * Math.PI * 2);
            double radius = reach * Math.sqrt(random.nextDouble());
            double angle = random.nextDouble() * Math.PI / 2;
            amplitudeU[i] = (float) (radius * Math.cos(angle));
            amplitudeV[i] = (float) (radius * Math.sin(angle));
            spin[i] = (float) ((0.06 + 0.2 * random.nextDouble()) * (random.nextBoolean() ? 1 : -1));
            spinPhase[i] = (float) (random.nextDouble() * Math.PI * 2);
            double z = random.nextDouble() * 2 - 1, azimuth = random.nextDouble() * Math.PI * 2, planar = Math.sqrt(1 - z * z);
            axisX[i] = (float) (planar * Math.cos(azimuth));
            axisY[i] = (float) (planar * Math.sin(azimuth));
            axisZ[i] = (float) z;
            tone[i] = random.nextInt(EffectPalette.SIZE);
        }
    }

    /** 返回环包含的粒子数量。 */
    public int count() {
        return count;
    }

    /** 返回轨道半径，单位为方块。 */
    public float majorRadius() {
        return majorRadius;
    }

    float phase(int index) {
        return phase[index];
    }

    float orbit(int index) {
        return orbit[index];
    }

    void spin(int index, float time, Matrix3f rotation) {
        rotation.rotation(spin[index] * time + spinPhase[index], axisX[index], axisY[index], axisZ[index]);
    }

    float radial(int index, double time) {
        return majorRadius + amplitudeU[index] * (float) Math.sin(driftPhaseU[index] + time * driftFrequencyU[index]);
    }

    float vertical(int index, double time) {
        return amplitudeV[index] * (float) Math.sin(driftPhaseV[index] + time * driftFrequencyV[index]);
    }

    int tone(int index) {
        return tone[index];
    }

    float half() {
        return half;
    }

    int dataRows() {
        return Math.ceilDiv(count, DATA_WIDTH / 4);
    }

    ByteBuffer createData() {
        var data = MemoryUtil.memCalloc(Math.multiplyExact(dataRows(), DATA_WIDTH * 4 * Float.BYTES));
        for (int i = 0; i < count; i++) {
            data.putFloat(phase[i]).putFloat(orbit[i]).putFloat(driftFrequencyU[i]).putFloat(driftFrequencyV[i]);
            data.putFloat(driftPhaseU[i]).putFloat(driftPhaseV[i]).putFloat(amplitudeU[i]).putFloat(amplitudeV[i]);
            data.putFloat(spin[i]).putFloat(spinPhase[i]).putFloat(tone[i]).putFloat(0);
            data.putFloat(axisX[i]).putFloat(axisY[i]).putFloat(axisZ[i]).putFloat(0);
        }
        data.clear();
        return data;
    }

    /**
     * 环效果中精灵、光晕与亮芯各自的宽度和强度。
     *
     * @param spriteScale    发光精灵相对基础像素的半宽倍率
     * @param spriteStrength 精灵强度，范围为 0～255
     * @param hazeWidth      光晕带半宽，单位为方块
     * @param hazeStrength   光晕带强度，范围为 0～255
     * @param coreWidth      亮芯带半宽，单位为方块
     * @param coreStrength   亮芯带强度，范围为 0～255
     */
    public record RingStyle(float spriteScale, int spriteStrength, float hazeWidth, int hazeStrength, float coreWidth, int coreStrength) {}

    /**
     * 定义闭环或由多条螺旋组成的环轮廓。螺旋沿环轴从原点开始延伸。
     *
     * @param strands 螺旋股数，0 表示闭环，最多 192 股
     * @param height  沿环轴的长度，单位为方块
     * @param turns   每股绕轴旋转的圈数，可用符号改变方向
     * @param taper   轴向分布从均匀变化到渐密的比例，范围为 0～0.95
     * @param fadeIn  开头逐渐显现的长度比例，范围为 0～1
     * @param fadeOut 结尾逐渐淡出的长度比例，范围为 0～1
     */
    public record RingShape(int strands, float height, float turns, float taper, float fadeIn, float fadeOut) {

        /** 在垂直于环轴的平面内闭合的环。 */
        public static final RingShape CLOSED = new RingShape(0, 0, 0, 0, 0, 0);

        public RingShape {
            if (strands < 0 || strands > RingRenderer.RIBBON_SEGMENTS) throw new IllegalArgumentException("螺旋股数超出范围");
            if (strands == 0) {
                height = turns = taper = fadeIn = fadeOut = 0;
            } else if (!Float.isFinite(height) || !Float.isFinite(turns) || height == 0 && turns == 0 ||
                    !(taper >= 0 && taper <= 0.95f && fadeIn >= 0 && fadeIn <= 1 && fadeOut >= 0 && fadeOut <= 1)) {
                        throw new IllegalArgumentException("螺旋轮廓参数无效");
                    }
        }

        /** 创建螺旋轮廓，渐密比例取 0～0.95，其余参数遵循 {@link RingShape} 的约定。 */
        public static RingShape helix(int strands, float height, float turns, float taper, float fadeIn, float fadeOut) {
            return new RingShape(strands, height, turns, Math.max(0, Math.min(taper, 0.95F)), fadeIn, fadeOut);
        }
    }
}
