package com.group6.cloudsim.evaluation;

import com.group6.cloudsim.core.SimulationConfig;
import com.group6.cloudsim.dispatch.WmrfLgConfig;
import com.group6.cloudsim.dispatch.WmrfLgPolicy;
import com.group6.cloudsim.workload.TaskMetadata;
import com.group6.cloudsim.workload.WorkloadGenerationConfig;
import com.group6.cloudsim.workload.WorkloadGenerator;
import com.group6.cloudsim.workload.WorkloadScenario;
import com.group6.cloudsim.workload.WorkloadTrace;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static com.group6.cloudsim.evaluation.ExperimentRunner.Ablation.*;
import static org.junit.jupiter.api.Assertions.*;

class FinalExperimentTest {
    private final WorkloadGenerationConfig config = WorkloadGenerationConfig.defaults(WorkloadScenario.BALANCED);
    private final WorkloadGenerator generator = new WorkloadGenerator();

    @Test void fingerprintMatchesRegenerationAndChangesWithActualTaskPayload() {
        var trace = generator.generate(config);
        assertTrue(trace.fingerprint().matches("[0-9a-f]{64}"));
        assertEquals(trace.fingerprint(), generator.generate(config).fingerprint());
        var altered = new ArrayList<>(trace.tasks());
        var original = altered.get(0);
        var profile = config.profiles().get(original.workloadClass());
        long newLength = original.lengthMiPerPe() == profile.minLengthMiPerPe()
            ? profile.maxLengthMiPerPe() : profile.minLengthMiPerPe();
        altered.set(0, new TaskMetadata(original.id(), original.workloadClass(), original.arrivalTimeSeconds(),
            newLength, original.pes(), original.ramDemandMb(), original.responseTimeTargetSeconds(), original.generationSeed()));
        assertNotEquals(trace.fingerprint(), new WorkloadTrace(config, altered).fingerprint());
        var longerHorizon = new WorkloadGenerationConfig(config.scenario(), config.totalTasks(), config.seed(),
            config.arrivalHorizonSeconds() + 1, config.arrivalRatePerSecond(), config.profiles());
        assertNotEquals(trace.fingerprint(), new WorkloadTrace(longerHorizon, trace.tasks()).fingerprint());
    }

    @Test void ablationOverridesOnlyItsIntendedCoefficients() {
        var defaults = new WmrfLgConfig(.5, .2, .3, 33, .4, .07, 20);
        assertEquals(defaults, WMRF_LG_FULL.settings(defaults));
        assertEquals(new WmrfLgConfig(.5, .2, .3, 33, 0, .07, 20), WMRF_LG_NO_LATENCY_GUARD.settings(defaults));
        assertEquals(new WmrfLgConfig(.5, .2, .3, 33, .4, 0, 20), WMRF_LG_NO_AGEING.settings(defaults));
        assertEquals(new WmrfLgConfig(.5, .2, .3, 33, 0, 0, 20), WMRF_LG_NO_LATENCY_NO_AGEING.settings(defaults));
    }

    @Test void fullVariantUsesFreshStateAndReproducesStandardPolicy() throws Exception {
        var trace = generator.generate(config);
        var purpose = ExperimentRunner.Purpose.ORIGINAL_STRESS;
        var simulation = SimulationConfig.research(trace);
        var settings = WmrfLgConfig.defaults();
        var runner = new ExperimentRunner();
        var standard = runner.runWmrfLg(new ExperimentRunner.Metadata("original-stress", purpose,
            WmrfLgPolicy.ID, trace, simulation), settings);
        var full = runner.runAblation(new ExperimentRunner.Metadata("original-stress", purpose,
            WMRF_LG_FULL.name(), trace, simulation), settings, WMRF_LG_FULL);
        assertEquals(standard.run().observations(), full.run().observations());
        assertEquals(standard.summary(), full.summary());
        assertEquals(standard.fairness(), full.fairness());
        assertNotSame(standard.run().completed().get(0).cloudlet(), full.run().completed().get(0).cloudlet());
        var csv = new StringWriter();
        CsvExporter.writeHeader(csv, true);
        CsvExporter.writeRun(csv, full, List.of(trace.fingerprint(), "test-run"));
        var rows = csv.toString().lines().toList();
        assertEquals(5, rows.size());
        assertTrue(rows.get(0).endsWith(",trace_sha256,run_id"));
        assertTrue(rows.subList(1, 5).stream().allMatch(row -> row.endsWith("," + trace.fingerprint() + ",test-run")));
    }
}
