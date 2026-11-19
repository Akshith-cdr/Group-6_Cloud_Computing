package com.group6.cloudsim.evaluation;

import com.group6.cloudsim.core.SimulationConfig;
import com.group6.cloudsim.core.SimulationConfig.MachineConfig;
import com.group6.cloudsim.workload.*;
import java.util.*;

/** Deterministic CPU lower bounds, assuming full CPU and no queueing or resource delays. */
public final class SlaFeasibilityAnalyzer {
    public enum Classification { NO_SLA, INTRINSICALLY_FEASIBLE, INTRINSICALLY_INFEASIBLE }

    public record TaskFeasibility(TaskMetadata task, double idealServiceSeconds, Classification classification) {}

    public record ClassFeasibility(int totalTasks, int slaTasks, int feasibleTasks, int infeasibleTasks) {
        public OptionalDouble feasiblePercentage() { return ratio(feasibleTasks * 100.0, slaTasks); }
        public OptionalDouble unavoidableMissLowerBound() { return ratio(infeasibleTasks, slaTasks); }
    }

    public record Report(List<TaskFeasibility> tasks, Map<WorkloadClass, ClassFeasibility> byClass) {
        public Report { tasks = List.copyOf(tasks); byClass = Map.copyOf(byClass); }
    }

    public Report analyze(WorkloadTrace trace, SimulationConfig config) {
        if (!trace.tasks().equals(config.tasks())) throw new IllegalArgumentException("Trace/config tasks differ");
        return analyze(trace.tasks(), List.of(config.vm()));
    }

    public Report analyze(List<TaskMetadata> tasks, List<MachineConfig> eligibleVms) {
        WorkloadValidator.validateTasks(tasks);
        var records = tasks.stream().map(task -> {
            double ideal = idealServiceSeconds(task, eligibleVms);
            var classification = task.workloadClass() == WorkloadClass.BATCH || task.responseTimeTargetSeconds().isEmpty() ? Classification.NO_SLA
                : ideal <= task.responseTimeTargetSeconds().getAsDouble() ? Classification.INTRINSICALLY_FEASIBLE
                : Classification.INTRINSICALLY_INFEASIBLE;
            return new TaskFeasibility(task, ideal, classification);
        }).toList();
        var classes = new EnumMap<WorkloadClass, ClassFeasibility>(WorkloadClass.class);
        for (var type : WorkloadClass.values()) {
            var group = records.stream().filter(r -> r.task().workloadClass() == type).toList();
            int feasible = (int) group.stream().filter(r -> r.classification() == Classification.INTRINSICALLY_FEASIBLE).count();
            int infeasible = (int) group.stream().filter(r -> r.classification() == Classification.INTRINSICALLY_INFEASIBLE).count();
            classes.put(type, new ClassFeasibility(group.size(), feasible + infeasible, feasible, infeasible));
        }
        return new Report(records, classes);
    }

    public double idealServiceSeconds(TaskMetadata task, List<MachineConfig> vms) {
        // Cloudlet length and scheduler progress are MI per PE, not aggregate MI.
        return vms.stream().filter(vm -> vm.pes() >= task.pes() && vm.ramMb() >= task.ramDemandMb())
            .mapToDouble(vm -> (double) task.lengthMiPerPe() / vm.mipsPerPe()).min()
            .orElse(Double.POSITIVE_INFINITY);
    }

    static OptionalDouble ratio(double numerator, long denominator) {
        return denominator == 0 ? OptionalDouble.empty() : OptionalDouble.of(numerator / denominator);
    }
}
