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
        OptionalDouble feasibleSlaMissRatio, OptionalDouble maximumQueueWait) {}

    public record Summary(Map<WorkloadClass, ClassMetrics> byClass, ClassMetrics aggregate,
                          OptionalDouble batchMakespan) {
        public Summary { byClass = Map.copyOf(byClass); }
    }

    /** Window starts at the workload epoch; tasks beyond an early cutoff remain unresolved. */
    public Map<WorkloadClass, ClassMetrics> collect(Report feasibility, List<TaskObservation> observations,
                                                 double windowStart, double windowEnd) {
        return summarize(feasibility, observations, windowStart, windowEnd).byClass();
    }

    public Summary summarize(Report feasibility, List<TaskObservation> observations, double windowStart, double windowEnd) {
        if (!Double.isFinite(windowStart) || !Double.isFinite(windowEnd) || windowStart < 0 || windowEnd < windowStart)
            throw new IllegalArgumentException("Invalid observation window");
        var byId = new HashMap<Long, TaskObservation>();
        for (var observation : observations) {
            if (byId.put(observation.task().id(), observation) != null) throw new IllegalArgumentException("Duplicate observation");
            double expectedArrival = windowStart + observation.task().arrivalTimeSeconds();
            if (!Double.isFinite(expectedArrival) || Math.abs(observation.workloadArrival() - expectedArrival) > 1e-8)
                throw new IllegalArgumentException("Workload arrival must equal epoch plus trace arrival");
            // Trace arrivals may lie beyond an early cutoff. Actual events must have happened
            // within the observation window, including events on unsuccessful tasks.
            for (var timestamp : new OptionalDouble[] {observation.readyQueueEntry(), observation.dispatch(),
                observation.cloudSimSubmission(), observation.executionStart(), observation.completion()}) {
                if (timestamp.isPresent() && (timestamp.getAsDouble() < windowStart - 1e-8
                    || timestamp.getAsDouble() > windowEnd + 1e-8))
                    throw new IllegalArgumentException("Observed event outside window");
            }
        }
        if (byId.size() != feasibility.tasks().size()) throw new IllegalArgumentException("Missing/extra observations");
        for (var row : feasibility.tasks()) {
            var observation = byId.get(row.task().id());
            if (observation == null || !row.task().equals(observation.task())) throw new IllegalArgumentException("Trace mismatch");
        }
        var result = new EnumMap<WorkloadClass, ClassMetrics>(WorkloadClass.class);
        double duration = windowEnd - windowStart;
        for (var type : WorkloadClass.values()) {
            var rows = feasibility.tasks().stream().filter(r -> r.task().workloadClass() == type).toList();
            result.put(type, compute(rows, byId, duration));
        }
        var batch = observations.stream().filter(o -> o.task().workloadClass() == WorkloadClass.BATCH).toList();
        var makespan = batch.isEmpty() || batch.stream().anyMatch(o -> o.outcome() != COMPLETED) ? OptionalDouble.empty()
            : OptionalDouble.of(batch.stream().mapToDouble(o -> o.completion().orElseThrow()).max().orElseThrow()
                - batch.stream().mapToDouble(TaskObservation::workloadArrival).min().orElseThrow());
        return new Summary(result, compute(feasibility.tasks(), byId, duration), makespan);
    }

    private static ClassMetrics compute(List<TaskFeasibility> rows, Map<Long, TaskObservation> byId, double duration) {
        var group = rows.stream().map(r -> byId.get(r.task().id())).toList();
        var completed = group.stream().filter(o -> o.outcome() == COMPLETED).toList();
        var responses = completed.stream().mapToDouble(o -> o.responseTime().orElseThrow()).sorted().toArray();
        int misses = 0, beyondLowerBound = 0, unresolved = 0, feasible = 0, infeasible = 0;
        for (var row : rows) {
            if (row.classification() == Classification.NO_SLA) continue;
            boolean withinBound = row.classification() == Classification.INTRINSICALLY_FEASIBLE;
            if (withinBound) feasible++;
            else infeasible++;
            var observation = byId.get(row.task().id());
            if (observation.outcome() != COMPLETED) { unresolved++; continue; }
            // Compare timestamps on the same clock so subtraction cannot push an exact deadline over the target.
            double deadline = observation.workloadArrival() + row.task().responseTimeTargetSeconds().orElseThrow();
            if (observation.completion().orElseThrow() > deadline) {
                misses++;
                if (withinBound) beyondLowerBound++;
            }
        }
        // A partial set of dispatch observations must not be mislabeled as a class average.
        double[] queueWaits = completed.stream().anyMatch(o -> o.queueWaitTime().isEmpty())
            ? new double[0] : completed.stream().mapToDouble(o -> o.queueWaitTime().orElseThrow()).toArray();
        return new ClassMetrics(completed.size(), count(group, FAILED), count(group, CANCELLED), count(group, UNFINISHED),
            Arrays.stream(responses).average(), median(responses), percentile(responses, .95), percentile(responses, .99),
            Arrays.stream(queueWaits).average(), duration == 0 ? OptionalDouble.empty() : OptionalDouble.of(completed.size() / duration),
            feasible + infeasible, feasible, infeasible, misses, beyondLowerBound, unresolved,
            ratio(misses, feasible + infeasible), ratio(beyondLowerBound, feasible), Arrays.stream(queueWaits).max());
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
