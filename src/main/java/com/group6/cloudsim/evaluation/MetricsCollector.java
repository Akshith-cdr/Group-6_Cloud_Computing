package com.group6.cloudsim.evaluation;

import com.group6.cloudsim.workload.*;
import java.util.*;
import static com.group6.cloudsim.evaluation.SlaFeasibilityAnalyzer.*;
import static com.group6.cloudsim.evaluation.TaskObservation.Outcome.*;

/** Policy-independent metrics. Missing observations and duplicate IDs fail closed. */
public final class MetricsCollector {
    public record ClassMetrics(int completed, int failed, int cancelled, int unfinished,
        OptionalDouble averageResponse, OptionalDouble medianResponse, OptionalDouble p95Response,
        OptionalDouble p99Response, OptionalDouble averageQueueWait, OptionalDouble throughput,
        int slaTasks, int intrinsicallyFeasible, int intrinsicallyInfeasible, int totalSlaMisses,
        int missesAmongFeasible, int unresolvedSlaTasks, OptionalDouble allSlaMissRatio,
        OptionalDouble feasibleSlaMissRatio) {}

    /** Window is [workload epoch, last terminal observation], shared by every workload class. */
    public Map<WorkloadClass, ClassMetrics> collect(Report feasibility, List<TaskObservation> observations,
                                                 double windowStart, double windowEnd) {
        if (!Double.isFinite(windowStart) || !Double.isFinite(windowEnd) || windowStart < 0 || windowEnd < windowStart)
            throw new IllegalArgumentException("Invalid observation window");
        var byId = new HashMap<Long, TaskObservation>();
        for (var observation : observations) {
            if (byId.put(observation.task().id(), observation) != null) throw new IllegalArgumentException("Duplicate observation");
            if (observation.workloadArrival() < windowStart || observation.workloadArrival() > windowEnd
                || observation.completion().orElse(windowEnd) > windowEnd)
                throw new IllegalArgumentException("Observation outside window");
        }
        if (byId.size() != feasibility.tasks().size()) throw new IllegalArgumentException("Missing/extra observations");
        for (var row : feasibility.tasks()) {
            var observation = byId.get(row.task().id());
            if (observation == null || !row.task().equals(observation.task())) throw new IllegalArgumentException("Trace mismatch");
        }
        var result = new EnumMap<WorkloadClass, ClassMetrics>(WorkloadClass.class);
        for (var type : WorkloadClass.values()) {
            var rows = feasibility.tasks().stream().filter(r -> r.task().workloadClass() == type).toList();
            var group = rows.stream().map(r -> byId.get(r.task().id())).toList();
            var completed = group.stream().filter(o -> o.outcome() == COMPLETED).toList();
            var responses = completed.stream().mapToDouble(o -> o.responseTime().orElseThrow()).sorted().toArray();
            int misses = 0, beyondLowerBound = 0, unresolved = 0;
            for (var row : rows) {
                if (row.classification() == Classification.NO_SLA) continue;
                var observation = byId.get(row.task().id());
                if (observation.outcome() != COMPLETED) { unresolved++; continue; }
                if (observation.responseTime().orElseThrow() > row.task().responseTimeTargetSeconds().orElseThrow()) {
                    misses++;
                    if (row.classification() == Classification.INTRINSICALLY_FEASIBLE) beyondLowerBound++;
                }
            }
            var bounds = feasibility.byClass().get(type);
            // A partial set of dispatch observations must not be mislabeled as a class average.
            var queueAverage = completed.isEmpty() || completed.stream().anyMatch(o -> o.queueWaitTime().isEmpty())
                ? OptionalDouble.empty() : completed.stream().mapToDouble(o -> o.queueWaitTime().orElseThrow()).average();
            result.put(type, new ClassMetrics(completed.size(), count(group, FAILED), count(group, CANCELLED), count(group, UNFINISHED),
                Arrays.stream(responses).average(), median(responses), percentile(responses, .95), percentile(responses, .99),
                queueAverage, windowEnd == windowStart ? OptionalDouble.empty() : OptionalDouble.of(completed.size() / (windowEnd-windowStart)),
                bounds.slaTasks(), bounds.feasibleTasks(), bounds.infeasibleTasks(), misses, beyondLowerBound, unresolved,
                ratio(misses, bounds.slaTasks()), ratio(beyondLowerBound, bounds.feasibleTasks())));
        }
        return Map.copyOf(result);
    }

    private static int count(List<TaskObservation> group, TaskObservation.Outcome outcome) {
        return (int) group.stream().filter(o -> o.outcome() == outcome).count();
    }
    private static OptionalDouble median(double[] sorted) {
        int n = sorted.length;
        return n == 0 ? OptionalDouble.empty() : OptionalDouble.of(n % 2 == 1 ? sorted[n/2] : (sorted[n/2-1] + sorted[n/2])/2);
    }
    /** Nearest-rank percentile; input is sorted. */
    private static OptionalDouble percentile(double[] sorted, double p) {
        return sorted.length == 0 ? OptionalDouble.empty() : OptionalDouble.of(sorted[(int)Math.ceil(p * sorted.length)-1]);
    }
}
