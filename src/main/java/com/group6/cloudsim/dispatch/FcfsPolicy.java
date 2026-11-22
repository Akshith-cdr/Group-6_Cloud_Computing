package com.group6.cloudsim.dispatch;

import com.group6.cloudsim.evaluation.SlaFeasibilityAnalyzer.TaskFeasibility;
import com.group6.cloudsim.workload.TaskMetadata;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Work-conserving first-come, first-served with feasible-task bypass. */
public final class FcfsPolicy implements DispatchPolicy {
    public static final String ID = "FCFS";

    @Override
    public Optional<Decision> select(double now, List<TaskFeasibility> ready, List<VmCapacity> vms,
                                     ResourceTimeAccountant service) {
        TaskMetadata selected = null;
        VmCapacity placement = null;
        for (var entry : ready) {
            var task = entry.task();
            if (task.arrivalTimeSeconds() > now || (selected != null && !earlier(task, selected))) continue;
            var vm = vms.stream().filter(v -> v.fits(entry)).min(Comparator.comparingLong(VmCapacity::id));
            if (vm.isEmpty()) continue;
            selected = task;
            placement = vm.get();
        }
        return selected == null ? Optional.empty() : Optional.of(new Decision(selected.id(), placement.id()));
    }

    private static boolean earlier(TaskMetadata candidate, TaskMetadata current) {
        double arrival = candidate.arrivalTimeSeconds();
        return arrival < current.arrivalTimeSeconds()
            || (arrival == current.arrivalTimeSeconds() && candidate.id() < current.id());
    }
}
