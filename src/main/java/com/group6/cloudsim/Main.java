package com.group6.cloudsim;

import com.group6.cloudsim.core.SimulationConfig;
import com.group6.cloudsim.core.SimulationController;
import com.group6.cloudsim.evaluation.*;
import com.group6.cloudsim.workload.WorkloadClass;
import com.group6.cloudsim.workload.WorkloadGenerationConfig;
import com.group6.cloudsim.workload.WorkloadGenerator;
import com.group6.cloudsim.workload.WorkloadScenario;
import com.group6.cloudsim.workload.WorkloadTrace;
import java.util.Locale;

/** Runs a generated research workload, or the preserved Milestone 1 smoke scenario. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--feasibility")) {
            try { EvaluationReport.main(java.util.Arrays.copyOfRange(args, 1, args.length)); }
            catch (Exception e) { throw new IllegalArgumentException("Feasibility analysis failed", e); }
            return;
        }
        SimulationConfig simulationConfig;
        WorkloadTrace researchTrace = null;
        if (args.length == 1 && args[0].equals("--smoke")) {
            simulationConfig = SimulationConfig.milestoneOne();
            System.out.println("Milestone 1 smoke scenario");
        } else {
            if (args.length > 5) throw new IllegalArgumentException(
                "Usage: [SCENARIO [TASK_COUNT [SEED [HORIZON_SECONDS [RATE_PER_SECOND]]]]] or --smoke");
            var scenario = args.length > 0 ? WorkloadScenario.valueOf(args[0].toUpperCase(Locale.ROOT)) : WorkloadScenario.BALANCED;
            var defaults = WorkloadGenerationConfig.defaults(scenario);
            var config = new WorkloadGenerationConfig(scenario,
                args.length > 1 ? Integer.parseInt(args[1]) : defaults.totalTasks(),
                args.length > 2 ? Long.parseLong(args[2]) : defaults.seed(),
                args.length > 3 ? Double.parseDouble(args[3]) : defaults.arrivalHorizonSeconds(),
                args.length > 4 ? Double.parseDouble(args[4]) : defaults.arrivalRatePerSecond(), defaults.profiles());
            var generator = new WorkloadGenerator();
            var trace = generator.generate(config);
            if (!trace.equals(generator.generate(config))) throw new IllegalStateException("Trace reproduction failed");
            printSummary(trace);
            System.out.println("Same-seed trace reproduction: PASS");
            simulationConfig = SimulationConfig.research(trace);
            researchTrace = trace;
        }
        java.util.List<SimulationController.CompletedTask> results;
        if (researchTrace == null) {
            results = new SimulationController().run(simulationConfig);
        } else {
            var metadata = new ExperimentRunner.Metadata("original-stress", ExperimentRunner.Purpose.ORIGINAL_STRESS,
                "cloudsim-stock-space-shared", researchTrace, simulationConfig);
            var experiment = new ExperimentRunner().runStock(metadata);
            EvaluationReport.printFeasibility(metadata.configurationName(), researchTrace, experiment.feasibility());
            EvaluationReport.printMetrics(experiment);
            results = experiment.run().completed();
        }
        if (!results.isEmpty()) {
            System.out.printf(Locale.ROOT, "%nWorkload epoch (simulation seconds): %.6f%n", results.get(0).workloadEpochSeconds());
            double maxError = results.stream().mapToDouble(result -> Math.abs(
                result.cloudlet().getDcArrivalTime() - result.expectedDatacenterArrivalSeconds())).max().orElseThrow();
            System.out.printf(Locale.ROOT, "Maximum datacenter-arrival error (seconds): %.9f%n", maxError);
        }
        System.out.printf("%nCompleted cloudlets (%d)%n", results.size());
        System.out.printf(
            "%-8s %-14s %-8s %3s %7s %10s %10s %10s%n",
            "Cloudlet", "Workload", "Status", "VM", "PEs", "Start (s)", "Finish (s)", "Run (s)"
        );
        for (var result : results) {
            var cloudlet = result.cloudlet();
            System.out.printf(
                Locale.ROOT, "%-8d %-14s %-8s %3d %7d %10.2f %10.2f %10.2f%n",
                cloudlet.getId(), result.metadata().workloadClass(), cloudlet.getStatus(),
                cloudlet.getVm().getId(), cloudlet.getPesNumber(),
                cloudlet.getStartTime(), cloudlet.getFinishTime(), cloudlet.getTotalExecutionTime()
            );
        }
    }

    private static void printSummary(WorkloadTrace trace) {
        var config = trace.config();
        System.out.printf("Scenario: %s%nSeed: %d%nTotal tasks: %d%n", config.scenario(), config.seed(), trace.tasks().size());
        System.out.printf(Locale.ROOT, "Arrival horizon: %.3f s; rate: %.3f tasks/s%n",
            config.arrivalHorizonSeconds(), config.arrivalRatePerSecond());
        if (!trace.tasks().isEmpty()) System.out.printf(Locale.ROOT, "Task arrival range: %.6f..%.6f s after workload epoch%n",
            trace.tasks().get(0).arrivalTimeSeconds(), trace.tasks().get(trace.tasks().size() - 1).arrivalTimeSeconds());
        for (var type : WorkloadClass.values()) {
            var tasks = trace.tasks().stream().filter(task -> task.workloadClass() == type).toList();
            System.out.printf("%s: %d%n", type, tasks.size());
            if (tasks.isEmpty()) continue;
            var lengths = tasks.stream().mapToLong(task -> task.lengthMiPerPe()).summaryStatistics();
            var pes = tasks.stream().mapToInt(task -> task.pes()).summaryStatistics();
            var ram = tasks.stream().mapToLong(task -> task.ramDemandMb()).summaryStatistics();
            System.out.printf("  Length: %d..%d MI/PE; PEs: %d..%d; RAM: %d..%d MB%n",
                lengths.getMin(), lengths.getMax(), pes.getMin(), pes.getMax(), ram.getMin(), ram.getMax());
        }
    }
}
