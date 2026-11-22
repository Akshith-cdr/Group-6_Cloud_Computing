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
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.LongStream;

/** Generates each trace once, then runs all policies and ablations against that immutable input. */
public final class FinalExperimentCommand {
    private static final List<String> POLICIES = List.of(FcfsPolicy.ID, StrictPriorityPolicy.ID,
        WeightedDrfPolicy.ID, WmrfLgPolicy.ID);
    private static final String COMPARATIVE = "comparative-results.csv";
    private static final String ABLATION = "wmrf-lg-ablation-results.csv";
    private static final String MANIFEST = "run-manifest.csv";

    private FinalExperimentCommand() {}

    public static void run(String[] args) throws IOException {
        if (args.length < 3 || args.length > 4)
            throw new IllegalArgumentException("Usage: --final-experiment NEW_DIRECTORY CANDIDATE_PROPERTIES [SEED,SEED,...]");
        long[] seeds = args.length == 4 ? Arrays.stream(args[3].split(",", -1)).mapToLong(Long::parseLong).distinct().sorted().toArray()
            : LongStream.rangeClosed(42, 61).toArray();
        var output = Files.createDirectory(Path.of(args[1]));
        var candidatePath = Path.of(args[2]);
        var settings = WmrfLgConfig.defaults();
        var runner = new ExperimentRunner();
        var generator = new WorkloadGenerator();
        int unsuccessful = 0;
        try (var comparative = open(output, COMPARATIVE);
             var ablation = open(output, ABLATION);
             var manifest = open(output, MANIFEST)) {
            CsvExporter.writeHeader(comparative, true);
            CsvExporter.writeHeader(ablation, true);
            manifest.write("run_id,policy,scenario,seed,configuration,purpose,trace_sha256,task_count,host_count,host,vm_count,vm,status,result_file,detail\n");
            for (var scenario : WorkloadScenario.values()) {
                var reference = WorkloadGenerationConfig.defaults(scenario);
                for (long seed : seeds) {
                    var config = new WorkloadGenerationConfig(scenario, reference.totalTasks(), seed,
                        reference.arrivalHorizonSeconds(), reference.arrivalRatePerSecond(), reference.profiles());
                    var original = generator.generate(config);
                    var candidate = Calibration.load(candidatePath, config);
                    unsuccessful += runCohort(original, "original-stress", ExperimentRunner.Purpose.ORIGINAL_STRESS,
                        runner, settings, comparative, ablation, manifest);
                    unsuccessful += runCohort(Calibration.generateTrace(candidate, original), candidate.name(),
                        ExperimentRunner.Purpose.UNAPPROVED_COMPARISON_CANDIDATE, runner, settings, comparative, ablation, manifest);
                }
                System.out.println("Finished " + scenario + " for " + seeds.length + " seeds");
            }
        }
        if (unsuccessful > 0) throw new IllegalStateException(unsuccessful + " unsuccessful runs. See " + output.resolve(MANIFEST));
        System.out.printf("Completed %d comparative and %d ablation runs in %s%n",
            WorkloadScenario.values().length * seeds.length * 2 * POLICIES.size(),
            WorkloadScenario.values().length * seeds.length * 2 * ExperimentRunner.Ablation.values().length, output);
    }

    private static Writer open(Path output, String name) throws IOException {
        return Files.newBufferedWriter(output.resolve(name), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static int runCohort(WorkloadTrace trace, String configuration, ExperimentRunner.Purpose purpose,
                                 ExperimentRunner runner, WmrfLgConfig settings, Writer comparative,
                                 Writer ablation, Writer manifest) throws IOException {
        var simulation = SimulationConfig.research(trace);
        var fingerprint = trace.fingerprint();
        int unsuccessful = 0;
        for (var policy : POLICIES) {
            var metadata = new ExperimentRunner.Metadata(configuration, purpose, policy, trace, simulation);
            unsuccessful += writeRun(metadata, fingerprint, COMPARATIVE, comparative, manifest,
                inputs -> ExperimentCommand.runPolicy(runner, inputs, settings));
        }
        for (var variant : ExperimentRunner.Ablation.values()) {
            var metadata = new ExperimentRunner.Metadata(configuration, purpose, variant.name(), trace, simulation);
            unsuccessful += writeRun(metadata, fingerprint, ABLATION, ablation, manifest,
                inputs -> runner.runAblation(inputs, settings, variant));
        }
        return unsuccessful;
    }

    private static int writeRun(ExperimentRunner.Metadata inputs, String fingerprint, String filename, Writer data,
                                 Writer manifest, Function<ExperimentRunner.Metadata, ExperimentRunner.Result> execute) throws IOException {
        var trace = inputs.trace().config();
        var sim = inputs.simulationConfig();
        String runId = String.join("/", inputs.policyName(), trace.scenario().name(), Long.toString(trace.seed()), inputs.configurationName());
        String status;
        String detail;
        try {
            var result = execute.apply(inputs);
            var counts = result.summary().aggregate();
            status = counts.completed() == trace.totalTasks() ? "SUCCESS" : "INCOMPLETE";
            detail = status.equals("SUCCESS") ? "" : "completed=%d failed=%d cancelled=%d unfinished=%d"
                .formatted(counts.completed(), counts.failed(), counts.cancelled(), counts.unfinished());
            CsvExporter.writeRun(data, result, List.of(fingerprint, runId));
            data.flush();
        } catch (RuntimeException e) {
            status = "ERROR";
            detail = e.toString();
        }
        CsvExporter.writeCsvRow(manifest, List.of(runId, inputs.policyName(), trace.scenario(), trace.seed(),
            inputs.configurationName(), inputs.purpose(), fingerprint, trace.totalTasks(),
            sim.hostCount(), sim.host(), sim.vmCount(), sim.vm(), status, filename, detail));
        manifest.flush();
        return status.equals("SUCCESS") ? 0 : 1;
    }
}
