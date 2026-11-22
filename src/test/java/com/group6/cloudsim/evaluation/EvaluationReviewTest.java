package com.group6.cloudsim.evaluation;

import com.group6.cloudsim.core.SimulationConfig;
import com.group6.cloudsim.workload.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.group6.cloudsim.evaluation.TaskObservation.Outcome.*;

class EvaluationReviewTest {
    @TempDir Path temporary;
    private static OptionalDouble at(double time) { return OptionalDouble.of(time); }
    private static TaskMetadata task(double arrival) {
        return new TaskMetadata(0, WorkloadClass.INTERACTIVE, arrival, 2000, 1, 512, at(5), 42);
    }
    private static SlaFeasibilityAnalyzer.Report report(TaskMetadata task) {
        return new SlaFeasibilityAnalyzer().analyze(List.of(task), List.of(SimulationConfig.milestoneOne().vm()));
    }

    @Test void rejectsArrivalUsingCloudSimSubmissionInsteadOfTraceClock() {
        var task = task(10);
        var wrongClock = new TaskObservation(task, COMPLETED, 100, OptionalDouble.empty(), OptionalDouble.empty(), at(100), at(110), at(112));
        assertThrows(IllegalArgumentException.class,
            () -> new MetricsCollector().collect(report(task), List.of(wrongClock), 100, 120));
    }

    @Test void earlyCutoffRetainsFutureTasksAsUnfinishedWithoutCompletingThem() {
        var task = task(100);
        var future = new TaskObservation(task, UNFINISHED, 200, OptionalDouble.empty(), OptionalDouble.empty(), at(100),
            OptionalDouble.empty(), OptionalDouble.empty());
        var metrics = new MetricsCollector().collect(report(task), List.of(future), 100, 110).get(WorkloadClass.INTERACTIVE);
        assertEquals(1, metrics.unfinished()); assertEquals(0, metrics.completed());
        assertEquals(1, metrics.unresolvedSlaTasks()); assertEquals(0, metrics.totalSlaMisses());
        assertTrue(metrics.averageResponse().isEmpty()); assertEquals(0, metrics.throughput().orElseThrow());
    }

    @Test void rejectsExecutionOrDispatchAfterObservationCutoff() {
        var task = task(0);
        var lateStart = new TaskObservation(task, UNFINISHED, 100, OptionalDouble.empty(), OptionalDouble.empty(), at(100), at(121), OptionalDouble.empty());
        var lateDispatch = new TaskObservation(task, UNFINISHED, 100, at(100), at(121), at(100), OptionalDouble.empty(), OptionalDouble.empty());
        assertThrows(IllegalArgumentException.class,
            () -> new MetricsCollector().collect(report(task), List.of(lateStart), 100, 120));
        assertThrows(IllegalArgumentException.class,
            () -> new MetricsCollector().collect(report(task), List.of(lateDispatch), 100, 120));
    }

    @Test void rejectsReadyEntryOrApiSubmissionAfterExecutionEvenWhenDispatchUnknown() {
        var task = task(0);
        assertThrows(IllegalArgumentException.class, () -> new TaskObservation(task, COMPLETED, 100,
            at(105), OptionalDouble.empty(), at(100), at(102), at(106)));
        assertThrows(IllegalArgumentException.class, () -> new TaskObservation(task, COMPLETED, 100,
            OptionalDouble.empty(), OptionalDouble.empty(), at(105), at(102), at(106)));
    }

    @Test void cannotLabelStockExecutionAsAnotherPolicy() {
        var trace = new WorkloadGenerator().generate(WorkloadGenerationConfig.defaults(WorkloadScenario.BALANCED));
        var metadata = new ExperimentRunner.Metadata("original-stress", ExperimentRunner.Purpose.ORIGINAL_STRESS,
            "WMRF-LG", trace, SimulationConfig.research(trace));
        assertThrows(IllegalArgumentException.class, () -> new ExperimentRunner().runStock(metadata));
    }

    @Test void lengthCalibrationPreservesExactTaskDemandsEvenWhenRandomSamplingRejectsDraws() throws Exception {
        var original = new WorkloadGenerator().generate(WorkloadGenerationConfig.defaults(WorkloadScenario.BALANCED));
        var path = temporary.resolve("wide-length.properties");
        Files.writeString(path, "name=wide-length-unapproved\nINTERACTIVE.maxLengthMiPerPe=4611686018427390000\n");
        var candidate = Calibration.load(path, original.config());
        var calibrated = Calibration.generateTrace(candidate, original);
        assertEquals(calibrated, Calibration.generateTrace(candidate, original));
        for (int i = 0; i < original.tasks().size(); i++) {
            var a = original.tasks().get(i); var b = calibrated.tasks().get(i);
            assertEquals(a.id(), b.id()); assertEquals(a.workloadClass(), b.workloadClass());
            assertEquals(a.arrivalTimeSeconds(), b.arrivalTimeSeconds());
            assertEquals(a.pes(), b.pes(), "Length changes must not alter PE draws, task " + a.id());
            assertEquals(a.ramDemandMb(), b.ramDemandMb(), "Length changes must not alter RAM draws, task " + a.id());
            if (a.workloadClass() != WorkloadClass.INTERACTIVE) assertEquals(a.lengthMiPerPe(), b.lengthMiPerPe());
        }
    }

    @Test void targetOnlyCalibrationRetainsAllWorkAndRejectsDifferentSourceControls() throws Exception {
        var original = new WorkloadGenerator().generate(WorkloadGenerationConfig.defaults(WorkloadScenario.BALANCED));
        var path = temporary.resolve("targets.properties");
        Files.writeString(path, "name=targets-unapproved\nINTERACTIVE.slaSeconds=10\nAI_INFERENCE.slaSeconds=50\n");
        var candidate = Calibration.load(path, original.config());
        var calibrated = Calibration.generateTrace(candidate, original);
        for (int i = 0; i < original.tasks().size(); i++) {
            var a = original.tasks().get(i); var b = calibrated.tasks().get(i);
            assertEquals(a.lengthMiPerPe(), b.lengthMiPerPe()); assertEquals(a.pes(), b.pes());
            assertEquals(a.ramDemandMb(), b.ramDemandMb()); assertEquals(a.arrivalTimeSeconds(), b.arrivalTimeSeconds());
        }
        var different = new WorkloadGenerator().generate(WorkloadGenerationConfig.defaults(WorkloadScenario.INFERENCE_HEAVY));
        assertThrows(IllegalArgumentException.class, () -> Calibration.generateTrace(candidate, different));
    }
}
