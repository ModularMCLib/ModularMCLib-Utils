package com.modularmc.mmclutils.persistence;

import lombok.Getter;
import lombok.experimental.Accessors;

/** 区分当前状态与已经成功写盘的版本。变更和快照由拥有者线程产生。 */
public final class ChangeTracker {

    @Getter
    @Accessors(fluent = true)
    private volatile long revision;
    private volatile long persisted;

    public void markChanged() {
        revision++;
    }

    public boolean isDirty() {
        return revision != persisted;
    }

    void persisted(long writtenRevision) {
        persisted = writtenRevision;
    }
}
