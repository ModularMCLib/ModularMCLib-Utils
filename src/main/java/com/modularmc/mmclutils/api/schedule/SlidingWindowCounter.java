package com.modularmc.mmclutils.api.schedule;

import java.util.Arrays;
import java.util.function.LongSupplier;

/**
 * 以滑动时间窗口约束操作速率，按访问时刻推进窗口。
 */
public class SlidingWindowCounter {

    private final LongSupplier timeSupplier;

    private final long[] usedAmounts;
    private final long[] updateTimes;

    private final int countingTimeframe;

    /** 创建固定长度的计量窗口。窗口长度须为正，时间源应单调递增并返回非负值。 */
    public SlidingWindowCounter(LongSupplier timeSupplier, int countingTimeframe) {
        this.timeSupplier = timeSupplier;
        this.countingTimeframe = countingTimeframe;

        this.usedAmounts = new long[countingTimeframe];
        this.updateTimes = new long[countingTimeframe];
    }

    /** 按当前时间统计有效窗口内的用量，窗口起点之前及起点上的槽位不参与统计。 */
    public long getUsedSum() {
        long excludeUntilTime = timeSupplier.getAsLong() - countingTimeframe;
        long sum = 0L;

        for (int i = 0; i < countingTimeframe; i++) {
            if (updateTimes[i] <= excludeUntilTime) continue;
            sum += usedAmounts[i];
        }

        return sum;
    }

    /** 用给定量更新当前时间所在槽位。时间推进到新的槽位时丢弃该位置的旧用量。 */
    public void addUsed(long amount) {
        long currentTime = timeSupplier.getAsLong();
        int idx = (int) (currentTime % countingTimeframe);

        if (updateTimes[idx] != currentTime)
            usedAmounts[idx] = 0L;

        usedAmounts[idx] += amount;
        updateTimes[idx] = currentTime;
    }

    /** 清零窗口中保存的用量，保留已分配的槽位。 */
    public void clear() {
        Arrays.fill(usedAmounts, 0L);
    }

    /** 复制窗口和历史用量。新计数器独立写入，继续使用同一时间源。 */
    public SlidingWindowCounter copy() {
        var copied = new SlidingWindowCounter(this.timeSupplier, this.countingTimeframe);
        System.arraycopy(this.usedAmounts, 0, copied.usedAmounts, 0, usedAmounts.length);
        System.arraycopy(this.updateTimes, 0, copied.updateTimes, 0, updateTimes.length);

        return copied;
    }
}
