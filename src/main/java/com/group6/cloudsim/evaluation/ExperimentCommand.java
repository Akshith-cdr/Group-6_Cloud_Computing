package com.group6.cloudsim.evaluation;

import com.group6.cloudsim.core.SimulationConfig;
import com.group6.cloudsim.dispatch.FcfsPolicy;
import com.group6.cloudsim.dispatch.StrictPriorityPolicy;
import com.group6.cloudsim.dispatch.WeightedDrfPolicy;
import com.group6.cloudsim.dispatch.WmrfLgConfig;
import com.group6.cloudsim.dispatch.WmrfLgPolicy;
import com.group6.cloudsim.workload.WorkloadGenerationConfig;
import com.group6.cloudsim.workload.WorkloadGenerator;
import com.group6.cloudsim.workload.WorkloadScenario;
import com.group6.cloudsim.workload.WorkloadTrace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/** Executes approved stress mixes and, when requested, a paired named candidate. */
public final class ExperimentCommand {
    private static final Set<String> POLICIES = Set.of(WmrfLgPolicy.ID, FcfsPolicy.ID, StrictPriorityPolicy.ID, WeightedDrfPolicy.ID);

    private ExperimentCommand() {}

    public static void run(String[] args) throws IOException {
        if (args.length < 3 || args.length % 2 == 0 || !POLICIES.contains(args[1]))
            throw new IllegalArgumentException(
                "Usage: --experiment <WMRF_LG|FCFS|STRICT_PRIORITY|WEIGHTED_DRF> OUTPUT [--scenario SCENARIO]"
                    + " [--seeds 42,43,44] [--candidate FILE] [--config FILE]");
        String policyId = args[1];
        Path output = Path.of(args[2]);
        long[] seeds = {42, 43, 44};
        Path candidatePath = null;
        var settings = WmrfLgConfig.defaults();
        var scenarios = WorkloadScenario.values();
        for (int i = 3; i < args.length; i += 2) {
            String value = args[i + 1];
            switch (args[i]) {
                case "--seeds" -> seeds = Arrays.stream(value.split(",", -1)).mapToLong(Long::parseLong).distinct().toArray();
                case "--candidate" -> candidatePath = Path.of(value);
                case "--config" -> settings = WmrfLgConfig.load(Path.of(value));
                case "--scenario" -> scenarios = new WorkloadScenario[] {WorkloadScenario.valueOf(value.toUpperCase(Locale.ROOT))};
                default -> throw new IllegalArgumentException("Unknown experiment option " + args[i]);
            }
        }
        var metadata = new java.util.ArrayList<ExperimentRunner.Metadata>();
        for (var scenario : scenarios) {
            var reference = WorkloadGenerationConfig.defaults(scenario);
            for (long seed : seeds) {
                var inputs = new WorkloadGenerationConfig(scenario, reference.totalTasks(), seed,
                    reference.arrivalHorizonSeconds(), reference.arrivalRatePerSecond(), reference.profiles());
                var original = new WorkloadGenerator().generate(inputs);
                metadata.add(metadata("original-stress", ExperimentRunner.Purpose.ORIGINAL_STRESS, policyId, original));
                if (candidatePath != null) {
                    var candidate = Calibration.load(candidatePath, inputs);
                    var trace = Calibration.generateTrace(candidate, original);
                    metadata.add(metadata(candidate.name(), ExperimentRunner.Purpose.UNAPPROVED_COMPARISON_CANDIDATE, policyId, trace));
                }
            }
        }
        var results = new java.util.ArrayList<ExperimentRunner.Result>(metadata.size());
        var runner = new ExperimentRunner();
        for (var inputs : metadata) {
            var result = runPolicy(runner, inputs, settings);
            results.add(result);
            EvaluationReport.printMetrics(result);
        }
        try (var writer = Files.newBufferedWriter(output)) {
            CsvExporter.writeHeader(writer);
            for (var result : results) CsvExporter.writeRun(writer, result);
        }
        System.out.printf("Exported %d runs to %s%n", results.size(), output);
    }

    static ExperimentRunner.Result runPolicy(ExperimentRunner runner, ExperimentRunner.Metadata inputs, WmrfLgConfig settings) {
        return switch (inputs.policyName()) {
            case FcfsPolicy.ID -> runner.runFcfs(inputs, settings);
            case StrictPriorityPolicy.ID -> runner.runStrictPriority(inputs, settings);
            case WeightedDrfPolicy.ID -> runner.runWeightedDrf(inputs, settings);
            case WmrfLgPolicy.ID -> runner.runWmrfLg(inputs, settings);
            default -> throw new IllegalArgumentException("Unsupported policy " + inputs.policyName());
        };
    }

    private static ExperimentRunner.Metadata metadata(String name, ExperimentRunner.Purpose purpose, String policyId, WorkloadTrace trace) {
        return new ExperimentRunner.Metadata(name, purpose, policyId, trace, SimulationConfig.research(trace));
    }
}
