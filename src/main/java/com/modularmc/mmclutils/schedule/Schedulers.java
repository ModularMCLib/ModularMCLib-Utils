package com.modularmc.mmclutils.schedule;

import com.modularmc.mmclutils.ModularMCLibUtils;
import com.modularmc.mmclutils.level.LevelStore;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * 取各车道的调度器，并把它们接到 NeoForge 的 Tick 与生命周期事件上。功能：
 * <ul>
 * <li>{@link #of}：该维度的主线程车道，在维度 tick 结束时推进一轮；客户端与服务端的维度各一份，维度卸载时关闭</li>
 * <li>{@link #async}：该维度的异步车道，每次主线程车道推进后若上一轮已结束就投给车道的线程池，没有到期任务时只推进轮次不占线程</li>
 * <li>{@link #server}/{@link #client}：不属于某个维度的全服与客户端车道，分别在服务器 tick、客户端 tick 结束时推进；停服、断开连接时关闭并换成新的一份</li>
 * <li>主线程与全局车道的任务抛出异常时按原版方块实体的做法生成带所有者信息的崩溃报告，所有者是作用域时写上作用域的路径与标签；异步车道只记日志</li>
 * <li>主线程车道建立时就认领驱动它的线程：服务端维度认领服务器线程，客户端维度认领客户端主线程（在第一个客户端 tick 记下，进世界之前主菜单就已在
 * tick）；全服车道在服务器即将启动时认领服务器线程，换新的客户端车道时认领客户端主线程；认领前别的线程上的 post、set、open 不会被误当成车道线程</li>
 * </ul>
 */
@EventBusSubscriber(modid = ModularMCLibUtils.MOD_ID)
public final class Schedulers {

    private static final TickScheduler.FailureHandler CRASH = Schedulers::crash;
    private static final LevelStore<TickScheduler> MAIN = LevelStore.createWithLevel(Schedulers::mainLane, TickScheduler::close);
    private static volatile @Nullable Thread clientThread;
    private static volatile TickScheduler server = new TickScheduler(true, CRASH);
    private static volatile TickScheduler client = new TickScheduler(true, CRASH);

    private Schedulers() {}

    private static TickScheduler mainLane(Level level) {
        TickScheduler lane = new TickScheduler(true, CRASH);
        Thread owner = level instanceof ServerLevel serverLevel ? serverLevel.getServer().getRunningThread() : level.isClientSide() ? clientThread : null;
        if (owner != null) lane.claim(owner);
        return lane;
    }

    public static TickScheduler of(Level level) {
        if (LevelStore.isReleased(level)) return TickScheduler.CLOSED;
        return MAIN.get(level);
    }

    public static TickScheduler async(Level level) {
        return AsyncLane.DEFAULT.scheduler(level);
    }

    public static TickScheduler async(Level level, AsyncLane lane) {
        return lane.scheduler(level);
    }

    public static TickScheduler server() {
        return server;
    }

    public static TickScheduler client() {
        return client;
    }

    @ApiStatus.Internal
    public static void resetClient() {
        TickScheduler old = client;
        TickScheduler fresh = new TickScheduler(true, CRASH);
        Thread thread = clientThread;
        if (thread != null) fresh.claim(thread);
        client = fresh;
        old.close();
    }

    @SubscribeEvent
    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        server.claim(event.getServer().getRunningThread());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    private static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        TickScheduler main = MAIN.getIfPresent(level);
        if (main != null) main.runRound();
        AsyncLane.kickAll(level);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    private static void onServerTick(ServerTickEvent.Post event) {
        server.runRound();
    }

    @ApiStatus.Internal
    public static void runClientTick() {
        if (clientThread == null) {
            clientThread = Thread.currentThread();
            client.claim(clientThread);
        }
        client.runRound();
    }

    @SubscribeEvent
    private static void onServerStopped(ServerStoppedEvent event) {
        TickScheduler old = server;
        server = new TickScheduler(true, CRASH);
        old.close();
    }

    private static void crash(Throwable error, @Nullable Object owner) {
        if (error instanceof ReportedException reported) throw reported;
        CrashReport report = CrashReport.forThrowable(error, "Running scheduled task");
        CrashReportCategory category = report.addCategory("Scheduled task");
        category.setDetail("Owner", owner == null ? "none" : owner.getClass().getName());
        if (owner instanceof Scope scope) category.setDetail("Scope", scope::toString);
        else if (owner instanceof BlockEntity be) be.fillCrashReportCategory(category);
        else if (owner instanceof Entity entity) entity.fillCrashReportCategory(category);
        throw new ReportedException(report);
    }
}
