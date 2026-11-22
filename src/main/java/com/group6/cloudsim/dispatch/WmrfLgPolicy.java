package com.group6.cloudsim.dispatch;

import com.group6.cloudsim.evaluation.SlaFeasibilityAnalyzer.Classification;
import com.group6.cloudsim.evaluation.SlaFeasibilityAnalyzer.TaskFeasibility;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Weighted dominant-service ordering with bounded simulated-time urgency and ageing. */
public final class WmrfLgPolicy implements DispatchPolicy {
    public static final String ID = "WMRF_LG";
    private final WmrfLgConfig config;

    public WmrfLgPolicy(WmrfLgConfig config) { this.config = config; }

    @Override
    public Optional<Decision> select(double now, List<TaskFeasibility> ready, List<VmCapacity> vms,
                                     ResourceTimeAccountant service) {
        var history = service.rollingShares(now);
        var ordering = Comparator.comparingDouble((Candidate c) -> c.score)
            .thenComparingDouble(c -> c.task.task().arrivalTimeSeconds())
            .thenComparingLong(c -> c.task.task().id())
            .thenComparingLong(c -> c.vm.id());
        Candidate best = null;
        for (var task : ready) {
            if (task.task().arrivalTimeSeconds() > now) continue;
            var vm = vms.stream().filter(v -> v.fits(task)).min(Comparator.comparingLong(VmCapacity::id));
            if (vm.isEmpty()) continue;
            var type = task.task().workloadClass();
            double dominant = history.get(type).dominant() / config.weight(type);
            var candidate = new Candidate(task, vm.get(), score(task, now, dominant));
            if (best == null || ordering.compare(candidate, best) < 0) best = candidate;
        }
        return Optional.ofNullable(best).map(c -> new Decision(c.task.task().id(), c.vm.id()));
    }

    double score(TaskFeasibility task, double now, double dominant) {
        return dominant - config.latencyCoefficient() * urgency(task, now)
            - config.ageingCoefficient() * ageing(task, now);
    }

    static double urgency(TaskFeasibility task, double now) {
        if (task.classification() != Classification.INTRINSICALLY_FEASIBLE) return 0;
        double target = task.task().responseTimeTargetSeconds().orElseThrow();
        double waiting = Math.max(0, now - task.task().arrivalTimeSeconds());
        return Math.min(1, waiting / target + task.idealServiceSeconds() / target);
    }

    double ageing(TaskFeasibility task, double now) {
        return Math.min(1, Math.max(0, now - task.task().arrivalTimeSeconds()) / config.ageingThresholdSeconds());
    }

    private record Candidate(TaskFeasibility task, VmCapacity vm, double score) {}
}
