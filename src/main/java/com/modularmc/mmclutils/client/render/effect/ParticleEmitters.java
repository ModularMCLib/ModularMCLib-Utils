package com.modularmc.mmclutils.client.render.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * 使用原版粒子工厂发射一次性高斯爆散，或在客户端 Tick 中持续发射粒子。
 * 须在客户端线程调用。生成时按相机距离裁剪，世界切换时清除仍在运行的发射器。
 */
public final class ParticleEmitters {

    static final int MAX_EMITTERS = 1024;

    private static final class Emitter {

        ParticleOptions particle;
        double x, y, z, dx, dy, dz, speed;
        int ticks, chance;
    }

    private static final ObjectArrayList<Emitter> ACTIVE = new ObjectArrayList<>();
    private static final ObjectArrayList<Emitter> POOL = new ObjectArrayList<>();

    private ParticleEmitters() {}

    /**
     * 立即使用原版工厂发射粒子。
     *
     * @param particle 原版粒子选项
     * @param x        中心世界坐标 X
     * @param y        中心世界坐标 Y
     * @param z        中心世界坐标 Z
     * @param count    爆散的粒子数。零时沿指定方向生成一个粒子
     * @param dx       X 轴位置偏移的标准差，数量为零时是方向 X
     * @param dy       Y 轴位置偏移的标准差，数量为零时是方向 Y
     * @param dz       Z 轴位置偏移的标准差，数量为零时是方向 Z
     * @param speed    各轴速度的标准差，数量为零时是方向速度倍率
     */
    public static void burst(ParticleOptions particle, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        var level = Minecraft.getInstance().level;
        if (level != null) spawn(level, particle, x, y, z, count, dx, dy, dz, speed);
    }

    /**
     * 从后续的客户端 Tick 开始，每 Tick 按概率发射一个粒子。
     * 持续时间耗尽后回收发射器，达到并发发射器上限时跳过本次登记。
     *
     * @param particle 原版粒子选项
     * @param x        中心世界坐标 X
     * @param y        中心世界坐标 Y
     * @param z        中心世界坐标 Z
     * @param ticks    持续 Tick 数，非正值时直接返回
     * @param chance   每 Tick 以该值的倒数为发射概率。小于 1 时按 1 处理
     * @param dx       X 轴位置偏移的标准差，单位为方块
     * @param dy       Y 轴位置偏移的标准差，单位为方块
     * @param dz       Z 轴位置偏移的标准差，单位为方块
     * @param speed    各轴速度的标准差，单位为方块每 Tick
     */
    public static void emitter(ParticleOptions particle, double x, double y, double z, int ticks, int chance, double dx, double dy, double dz,
                               double speed) {
        if (ticks <= 0 || ACTIVE.size() >= MAX_EMITTERS) return;
        var e = POOL.isEmpty() ? new Emitter() : POOL.remove(POOL.size() - 1);
        e.particle = particle;
        e.x = x;
        e.y = y;
        e.z = z;
        e.dx = dx;
        e.dy = dy;
        e.dz = dz;
        e.speed = speed;
        e.ticks = ticks;
        e.chance = Math.max(1, chance);
        ACTIVE.add(e);
    }

    static void tick(ClientLevel level) {
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            var e = ACTIVE.get(i);
            if (e.chance == 1 || level.getRandom().nextInt(e.chance) == 0) spawn(level, e.particle, e.x, e.y, e.z, 1, e.dx, e.dy, e.dz, e.speed);
            if (--e.ticks <= 0) remove(i);
        }
    }

    static void clear() {
        for (int i = ACTIVE.size() - 1; i >= 0; i--) remove(i);
    }

    private static void remove(int i) {
        int last = ACTIVE.size() - 1;
        var e = ACTIVE.get(i);
        ACTIVE.set(i, ACTIVE.get(last));
        ACTIVE.remove(last);
        e.particle = null;
        POOL.add(e);
    }

    private static void spawn(ClientLevel level, ParticleOptions particle, double x, double y, double z, int count, double dx, double dy, double dz,
                              double speed) {
        var eye = Minecraft.getInstance().gameRenderer.getMainCamera().position();
        if (eye.distanceToSqr(x, y, z) > ParticleEffects.SPAWN_DISTANCE * ParticleEffects.SPAWN_DISTANCE) return;
        var random = level.getRandom();
        if (count == 0) {
            level.addParticle(particle, true, false, x, y, z, speed * dx, speed * dy, speed * dz);
            return;
        }
        for (int i = 0; i < count; i++) {
            level.addParticle(particle, true, false, x + random.nextGaussian() * dx, y + random.nextGaussian() * dy, z + random.nextGaussian() * dz,
                    random.nextGaussian() * speed, random.nextGaussian() * speed, random.nextGaussian() * speed);
        }
    }
}
