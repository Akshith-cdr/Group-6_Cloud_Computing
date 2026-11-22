package com.group6.cloudsim.dispatch;

import com.group6.cloudsim.evaluation.SlaFeasibilityAnalyzer.TaskFeasibility;
import java.util.List;
import java.util.Optional;

/** A policy sees ready tasks, current VM capacity and historical service in workload seconds. */
@FunctionalInterface
public interface DispatchPolicy {
    Optional<Decision> select(double now, List<TaskFeasibility> ready, List<VmCapacity> vms,
                              ResourceTimeAccountant service);

    /** Policies that need instantaneous reservations receive a fresh ledger snapshot at each decision. */
    @FunctionalInterface
    interface WithCurrentAllocations extends DispatchPolicy {
        Optional<Decision> selectWithAllocations(double now, List<TaskFeasibility> ready, List<VmCapacity> vms,
                                                 ReservationSnapshot allocations);

        @Override
        default Optional<Decision> select(double now, List<TaskFeasibility> ready, List<VmCapacity> vms,
                                          ResourceTimeAccountant service) {
            throw new IllegalStateException("This policy requires a current VM reservation snapshot");
        }
    }

    record VmCapacity(long id, long freePes, long freeRamMb) {
        public boolean fits(TaskFeasibility task) {
            return freePes >= task.task().pes() && freeRamMb >= task.task().ramDemandMb();
        }
    }

    record Decision(long taskId, long vmId) {}
}
