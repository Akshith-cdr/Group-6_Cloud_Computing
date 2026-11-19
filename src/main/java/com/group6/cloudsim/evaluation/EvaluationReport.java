package com.group6.cloudsim.evaluation;

import com.group6.cloudsim.core.SimulationConfig;
import com.group6.cloudsim.workload.*;
import java.nio.file.Path;
import java.util.*;

/** Stable CLI reporting. Candidate files are explicit and never selected automatically. */
public final class EvaluationReport {
    private EvaluationReport() {}
    public static String value(OptionalDouble value) {
        return value.isPresent() ? String.format(Locale.ROOT, "%.6f", value.getAsDouble()) : "N/A";
    }
    public static void printFeasibility(String name, WorkloadTrace trace, SlaFeasibilityAnalyzer.Report report) {
        System.out.printf("Feasibility: configuration=%s scenario=%s seed=%d%n", name, trace.config().scenario(), trace.config().seed());
        System.out.println("class,total,sla,feasible,infeasible,feasible_percent,unavoidable_miss_lower_bound");
        for (var type : WorkloadClass.values()) {
            var r = report.byClass().get(type);
            System.out.printf("%s,%d,%d,%d,%d,%s,%s%n", type, r.totalTasks(), r.slaTasks(), r.feasibleTasks(),
                r.infeasibleTasks(), value(r.feasiblePercentage()), value(r.unavoidableMissLowerBound()));
        }
    }
    public static void printMetrics(ExperimentRunner.Result result) {
        var metadata = result.metadata();
        System.out.printf("Experiment: configuration=%s purpose=%s policy=%s seed=%d window=%.6f..%.6f%n",
            metadata.configurationName(), metadata.purpose(), metadata.policyName(), metadata.trace().config().seed(),
            result.run().workloadEpoch(), result.run().observationEnd());
        System.out.println("class,completed,failed,cancelled,unfinished,mean_response,median_response,p95,p99,mean_queue_wait,throughput,sla_tasks,intrinsic_infeasible,misses,misses_feasible,unresolved_sla,all_sla_miss_ratio,feasible_sla_miss_ratio");
        for (var type : WorkloadClass.values()) {
            var m = result.metrics().get(type);
            System.out.printf("%s,%d,%d,%d,%d,%s,%s,%s,%s,%s,%s,%d,%d,%d,%d,%d,%s,%s%n", type,
                m.completed(), m.failed(), m.cancelled(), m.unfinished(), value(m.averageResponse()), value(m.medianResponse()),
                value(m.p95Response()), value(m.p99Response()), value(m.averageQueueWait()), value(m.throughput()),
                m.slaTasks(), m.intrinsicallyInfeasible(), m.totalSlaMisses(), m.missesAmongFeasible(), m.unresolvedSlaTasks(),
                value(m.allSlaMissRatio()), value(m.feasibleSlaMissRatio()));
        }
        System.out.println("Stock broker: ready/dispatch timestamps and CPU/RAM time averages unavailable; policy comparisons NOT ready.");
    }

    /** Runs all four scenarios with original stress plus zero or more named candidate property files. */
    public static void main(String[] args) throws Exception {
        for (var scenario : WorkloadScenario.values()) {
            var original = WorkloadGenerationConfig.defaults(scenario);
            print("original-stress", original);
            var names = new HashSet<String>();
            for (var argument : args) {
                var candidate = Calibration.load(Path.of(argument), original);
                if (!names.add(candidate.name())) throw new IllegalArgumentException("Duplicate candidate name: " + candidate.name());
                print(candidate.name() + " (UNAPPROVED)", candidate.config());
            }
        }
    }
    private static void print(String name, WorkloadGenerationConfig config) {
        var trace = new WorkloadGenerator().generate(config);
        printFeasibility(name, trace, new SlaFeasibilityAnalyzer().analyze(trace, SimulationConfig.research(trace)));
        for (var type : WorkloadClass.values()) System.out.println("  " + type + " profile=" + config.profiles().get(type));
    }
}
