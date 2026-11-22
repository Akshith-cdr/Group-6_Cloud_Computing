package com.group6.cloudsim.dispatch;

import com.group6.cloudsim.evaluation.SlaFeasibilityAnalyzer.TaskFeasibility;
import com.group6.cloudsim.workload.TaskMetadata;
import com.group6.cloudsim.workload.WorkloadClass;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Fixed class priority with feasible-task bypass and FCFS within each class. */
public final class StrictPriorityPolicy implements DispatchPolicy {
    public static final String ID = "STRICT_PRIORITY";
    private static final Comparator<VmCapacity> BY_VM_ID = Comparator.comparingLong(VmCapacity::id);

    @Override
    public Optional<Decision> select(double now, List<TaskFeasibility> ready, List<VmCapacity> vms,
                                     ResourceTimeAccountant service) {
        TaskMetadata selected = null;
        VmCapacity placement = null;
        for (var entry : ready) {
            var task = entry.task();
            if (task.arrivalTimeSeconds() > now || (selected != null && !precedes(task, selected))) continue;
            var vm = vms.stream().filter(v -> v.fits(entry)).min(BY_VM_ID);
            if (vm.isEmpty()) continue;
            selected = task;
            placement = vm.get();
        }
        return selected == null ? Optional.empty() : Optional.of(new Decision(selected.id(), placement.id()));
    }

    private static boolean precedes(TaskMetadata candidate, TaskMetadata current) {
        int candidateRank = rank(candidate.workloadClass());
        int currentRank = rank(current.workloadClass());
        if (candidateRank != currentRank) return candidateRank < currentRank;
        double arrival = candidate.arrivalTimeSeconds();
        return arrival < current.arrivalTimeSeconds()
            || (arrival == current.arrivalTimeSeconds() && candidate.id() < current.id());
    }

    private static int rank(WorkloadClass type) {
        return switch (type) {
            case INTERACTIVE -> 1;
            case AI_INFERENCE -> 2;
            case BATCH -> 3;
        };
    }
}
