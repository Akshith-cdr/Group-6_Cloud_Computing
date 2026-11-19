package com.group6.cloudsim.workload;

/**
 * Extension contract for a future task-level RAM reservation implementation.
 * This milestone neither implements nor installs a ledger. Callers would reserve before dispatch
 * and release on completion/cancellation. Implementations must atomically reject over-capacity
 * reservations, track (vmId, task.id()) uniquely, and make release idempotent.
 */
public interface RamAdmissionLedger {
    boolean tryReserve(long vmId, long vmCapacityMb, TaskMetadata task);
    void release(long vmId, long taskId);
}
