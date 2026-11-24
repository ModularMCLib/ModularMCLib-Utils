package com.modularmc.mmclutils.client.level;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/** 用原始类型分页存储占用区间的端点。采集结束后由后台只读访问。 */
public final class SectionOccupancy {

    private static final int PAGE_BITS = 10;
    private static final int PAGE_SIZE = 1 << PAGE_BITS;
    private final ObjectArrayList<long[]> pages = new ObjectArrayList<>();
    private int size;

    /** 在采集线程按升序成对追加区间起点和终点。区间包含起点，排除终点。 */
    public void append(long value) {
        int offset = size & (PAGE_SIZE - 1);
        if (offset == 0) pages.add(new long[PAGE_SIZE]);
        pages.get(size++ >> PAGE_BITS)[offset] = value;
    }

    public boolean empty() {
        return size == 0;
    }

    public boolean contains(long cell) {
        int low = 0, high = size;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (get(middle) <= cell) low = middle + 1;
            else high = middle;
        }
        return (low & 1) != 0;
    }

    public boolean fills(long cells) {
        return size == 2 && get(0) == 0 && get(1) == cells;
    }

    private long get(int index) {
        return pages.get(index >> PAGE_BITS)[index & (PAGE_SIZE - 1)];
    }
}
