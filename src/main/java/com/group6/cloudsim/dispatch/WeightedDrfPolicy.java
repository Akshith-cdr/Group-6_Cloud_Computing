package com.group6.cloudsim.dispatch;

import com.group6.cloudsim.evaluation.SlaFeasibilityAnalyzer.TaskFeasibility;
import com.group6.cloudsim.workload.TaskMetadata;
import com.group6.cloudsim.workload.WorkloadClass;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/** Class-level weighted DRF-inspired scheduling with feasible-task bypass. */
public final class WeightedDrfPolicy implements DispatchPolicy.WithCurrentAllocations {
    public static final String ID = "WEIGHTED_DRF";
    private static final Comparator<VmCapacity> BY_VM_ID = Comparator.comparingLong(VmCapacity::id);
    private static final Comparator<Candidate> ORDER = Comparator.comparingDouble(Candidate::weightedShare)
        .thenComparing(c -> c.task().workloadClass().name())
        .thenComparingDouble(c -> c.task().arrivalTimeSeconds() + 0.0) // Signed zeros are the same arrival.
        .thenComparingLong(c -> c.task().id());
    private final ToDoubleFunction<WorkloadClass> weight;

    public WeightedDrfPolicy(WmrfLgConfig referenceConfig) {
        weight = Objects.requireNonNull(referenceConfig)::weight;
    }

    @Override
    public Optional<Decision> selectWithAllocations(double now, List<TaskFeasibility> ready, List<VmCapacity> vms,
                                                    ReservationSnapshot allocations) {
        Objects.requireNonNull(allocations, "Current VM reservations are required");
        var shares = new EnumMap<WorkloadClass, Double>(WorkloadClass.class);
        for (var type : WorkloadClass.values()) shares.put(type, allocations.dominantShare(type) / weight.applyAsDouble(type));

        Candidate best = null;
        for (var entry : ready) {
            var task = entry.task();
            if (task.arrivalTimeSeconds() > now) continue;
            var placement = vms.stream().filter(vm -> vm.fits(entry)).min(BY_VM_ID);
            if (placement.isEmpty()) continue;
            var candidate = new Candidate(task, placement.get().id(), shares.get(task.workloadClass()));
            if (best == null || ORDER.compare(candidate, best) < 0) best = candidate;
        }
        return Optional.ofNullable(best).map(c -> new Decision(c.task().id(), c.vmId()));
    }

    private record Candidate(TaskMetadata task, long vmId, double weightedShare) {}
}
