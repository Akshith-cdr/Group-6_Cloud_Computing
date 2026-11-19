package com.group6.cloudsim.evaluation;

import com.group6.cloudsim.core.*;
import com.group6.cloudsim.workload.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static com.group6.cloudsim.evaluation.TaskObservation.Outcome.*;
import static com.group6.cloudsim.evaluation.SlaFeasibilityAnalyzer.Classification.*;

class EvaluationTest {
    private final SlaFeasibilityAnalyzer analyzer = new SlaFeasibilityAnalyzer();
    private final SimulationConfig.MachineConfig vm = new SimulationConfig.MachineConfig(2, 1000, 4096, 1000, 20000);
    @TempDir Path temporary;

    private static TaskMetadata task(long id, WorkloadClass type, long length, int pes) {
        return new TaskMetadata(id, type, 1, length, pes, 512,
            type == WorkloadClass.BATCH ? OptionalDouble.empty() : OptionalDouble.of(5), 42);
    }
    private static OptionalDouble at(double time) { return OptionalDouble.of(time); }
    private static TaskObservation completed(TaskMetadata t, double response) {
        return new TaskObservation(t, COMPLETED, 101, at(102), at(103), at(100), at(104), at(101 + response));
    }
    private static TaskObservation other(TaskMetadata t, TaskObservation.Outcome outcome) {
        return new TaskObservation(t, outcome, 101, OptionalDouble.empty(), OptionalDouble.empty(), at(100),
            OptionalDouble.empty(), OptionalDouble.empty());
    }

    @Test void serviceIsPerPeAndFastestEligibleVmWins() {
        var two = task(0, WorkloadClass.AI_INFERENCE, 12000, 2);
        assertEquals(12, analyzer.idealServiceSeconds(two, List.of(vm)));
        var tooSmall = new SimulationConfig.MachineConfig(1, 4000, 8192, 1000, 20000);
        var tooLittleRam = new SimulationConfig.MachineConfig(2, 8000, 256, 1000, 20000);
        var faster = new SimulationConfig.MachineConfig(2, 2000, 4096, 1000, 20000);
        assertEquals(6, analyzer.idealServiceSeconds(two, List.of(vm, tooSmall, tooLittleRam, faster)));
        assertEquals(Double.POSITIVE_INFINITY, analyzer.idealServiceSeconds(two, List.of(tooSmall, tooLittleRam)));
    }

    @Test void classifiesBoundaryInfeasibleAndBatchWithoutSla() {
        var tasks = List.of(task(0, WorkloadClass.INTERACTIVE, 5000, 1),
            task(1, WorkloadClass.INTERACTIVE, 5001, 1), task(2, WorkloadClass.BATCH, 20000, 2));
        var report = analyzer.analyze(tasks, List.of(vm));
        assertEquals(List.of(INTRINSICALLY_FEASIBLE, INTRINSICALLY_INFEASIBLE, NO_SLA),
            report.tasks().stream().map(SlaFeasibilityAnalyzer.TaskFeasibility::classification).toList());
        var interactive = report.byClass().get(WorkloadClass.INTERACTIVE);
        assertEquals(2, interactive.totalTasks()); assertEquals(2, interactive.slaTasks());
        assertEquals(50, interactive.feasiblePercentage().orElseThrow());
        assertEquals(.5, interactive.unavoidableMissLowerBound().orElseThrow());
        var batch = report.byClass().get(WorkloadClass.BATCH);
        assertEquals(0, batch.slaTasks()); assertTrue(batch.feasiblePercentage().isEmpty());
        assertTrue(batch.unavoidableMissLowerBound().isEmpty());
        var mislabeledBatch = new TaskMetadata(0, WorkloadClass.BATCH, 0, 20000, 1, 512, at(1), 42);
        assertEquals(NO_SLA, analyzer.analyze(List.of(mislabeledBatch), List.of(vm)).tasks().get(0).classification());
        assertThrows(UnsupportedOperationException.class, () -> report.tasks().clear());
    }

    @Test void ineligibleVmHasInfiniteCpuBound() {
        var report = analyzer.analyze(List.of(task(0, WorkloadClass.INTERACTIVE, 1, 3)), List.of(vm));
        assertEquals(INTRINSICALLY_INFEASIBLE, report.tasks().get(0).classification());
    }

    @Test void timestampContractSeparatesArrivalQueueDelayAndService() {
        var observation = new TaskObservation(task(0, WorkloadClass.INTERACTIVE, 2000, 1), COMPLETED,
            100, at(101), at(104), at(99), at(106), at(108));
        assertEquals(8, observation.responseTime().orElseThrow());
        assertEquals(4, observation.queueWaitTime().orElseThrow());
        assertEquals(2, observation.preExecutionDelay().orElseThrow());
        assertEquals(2, observation.serviceTime().orElseThrow());
        assertEquals(observation.responseTime().orElseThrow(), observation.queueWaitTime().orElseThrow()
            + observation.preExecutionDelay().orElseThrow() + observation.serviceTime().orElseThrow());
    }

    @Test void missingTimestampsAreNotInventedAndInvalidCompletionIsRejected() {
        var t = task(0, WorkloadClass.INTERACTIVE, 2000, 1);
        assertTrue(other(t, UNFINISHED).responseTime().isEmpty());
        assertTrue(other(t, CANCELLED).queueWaitTime().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> other(t, COMPLETED));
        assertThrows(IllegalArgumentException.class, () -> new TaskObservation(t, FAILED, 100, at(101), at(102), at(99), at(103), at(104)));
        assertThrows(IllegalArgumentException.class, () -> new TaskObservation(t, COMPLETED, 100, at(101), at(99), at(99), at(103), at(104)));
    }

    @Test void completedOnlyMetricsUseAllSlaAndFeasibleDenominatorsAndTrackUnresolved() {
        var tasks = List.of(task(0, WorkloadClass.BATCH, 1000, 1), task(1, WorkloadClass.INTERACTIVE, 2000, 1),
            task(2, WorkloadClass.INTERACTIVE, 10000, 1), task(3, WorkloadClass.INTERACTIVE, 2000, 1),
            task(4, WorkloadClass.INTERACTIVE, 2000, 1), task(5, WorkloadClass.INTERACTIVE, 2000, 1));
        var observations = List.of(completed(tasks.get(0), 4), completed(tasks.get(1), 10), completed(tasks.get(2), 12),
            other(tasks.get(3), FAILED), other(tasks.get(4), CANCELLED), other(tasks.get(5), UNFINISHED));
        var metrics = new MetricsCollector().collect(analyzer.analyze(tasks, List.of(vm)), observations, 100, 120);
        var m = metrics.get(WorkloadClass.INTERACTIVE);
        assertEquals(2, m.completed()); assertEquals(1, m.failed()); assertEquals(1, m.cancelled()); assertEquals(1, m.unfinished());
        assertEquals(5, m.slaTasks()); assertEquals(4, m.intrinsicallyFeasible()); assertEquals(1, m.intrinsicallyInfeasible());
        assertEquals(2, m.totalSlaMisses()); assertEquals(1, m.missesAmongFeasible()); assertEquals(3, m.unresolvedSlaTasks());
        assertEquals(.4, m.allSlaMissRatio().orElseThrow()); assertEquals(.25, m.feasibleSlaMissRatio().orElseThrow());
        assertEquals(11, m.averageResponse().orElseThrow()); assertEquals(11, m.medianResponse().orElseThrow());
        assertEquals(12, m.p95Response().orElseThrow()); assertEquals(12, m.p99Response().orElseThrow());
        assertEquals(2, m.averageQueueWait().orElseThrow()); assertEquals(.1, m.throughput().orElseThrow());
        assertEquals(0, metrics.get(WorkloadClass.BATCH).totalSlaMisses());
        assertTrue(metrics.get(WorkloadClass.BATCH).allSlaMissRatio().isEmpty());
    }

    @Test void missingOrDuplicateResultsCannotSilentlyImproveRatios() {
        var t = task(0, WorkloadClass.INTERACTIVE, 2000, 1);
        var report = analyzer.analyze(List.of(t), List.of(vm));
        var collector = new MetricsCollector();
        assertThrows(IllegalArgumentException.class, () -> collector.collect(report, List.of(), 100, 120));
        assertThrows(IllegalArgumentException.class, () -> collector.collect(report, List.of(completed(t, 10), completed(t, 10)), 100, 120));
        assertThrows(IllegalArgumentException.class, () -> collector.collect(report, List.of(completed(t, 10)), 100, 105));
    }

    @Test void emptyClassHasNoLatencyOrSlaDenominator() {
        var metrics = new MetricsCollector().collect(analyzer.analyze(List.of(), List.of(vm)), List.of(), 0, 0);
        for (var m : metrics.values()) {
            assertEquals(0, m.completed()); assertTrue(m.averageResponse().isEmpty());
            assertTrue(m.feasibleSlaMissRatio().isEmpty()); assertTrue(m.throughput().isEmpty());
        }
    }

    @ParameterizedTest @EnumSource(WorkloadScenario.class)
    void sameTraceSameReportAndCandidateDoesNotChangeOriginal(WorkloadScenario scenario) throws Exception {
        var original = WorkloadGenerationConfig.defaults(scenario);
        var trace = new WorkloadGenerator().generate(original);
        var regenerated = new WorkloadGenerator().generate(original);
        assertEquals(analyzer.analyze(trace, SimulationConfig.research(trace)),
            new SlaFeasibilityAnalyzer().analyze(regenerated, SimulationConfig.research(regenerated)));
        var candidate = Calibration.load(Path.of("experiments/service-bound-demo.properties"), original);
        assertNotEquals(original, candidate.config());
        assertEquals(original, WorkloadGenerationConfig.defaults(scenario));
        var alternative = Calibration.generateTrace(candidate, trace);
        for (int i = 0; i < trace.tasks().size(); i++) {
            var a = trace.tasks().get(i); var b = alternative.tasks().get(i);
            assertEquals(a.arrivalTimeSeconds(), b.arrivalTimeSeconds()); assertEquals(a.workloadClass(), b.workloadClass());
            assertEquals(a.pes(), b.pes()); assertEquals(a.ramDemandMb(), b.ramDemandMb());
        }
        var report = analyzer.analyze(alternative, SimulationConfig.research(alternative));
        for (var m : report.byClass().values()) assertEquals(0, m.infeasibleTasks());
    }

    @Test void approvedDefaultValuesAreUnchanged() {
        var defaults = WorkloadGenerationConfig.defaults(WorkloadScenario.BALANCED);
        assertEquals(120, defaults.totalTasks()); assertEquals(42, defaults.seed());
        assertEquals(600, defaults.arrivalHorizonSeconds()); assertEquals(.5, defaults.arrivalRatePerSecond());
        assertEquals(new WorkloadProfile(30000, 120000, 1, 2, 1024, 2048, OptionalDouble.empty()), defaults.profiles().get(WorkloadClass.BATCH));
        assertEquals(new WorkloadProfile(2000, 10000, 1, 1, 256, 1024, at(5)), defaults.profiles().get(WorkloadClass.INTERACTIVE));
        assertEquals(new WorkloadProfile(10000, 50000, 1, 2, 1536, 3072, at(10)), defaults.profiles().get(WorkloadClass.AI_INFERENCE));
        var config = SimulationConfig.research(new WorkloadGenerator().generate(defaults));
        assertEquals(3, config.hostCount()); assertEquals(6, config.vmCount()); assertEquals(vm, config.vm());
        assertEquals(new SimulationConfig.MachineConfig(4,1000,16384,10000,1000000), config.host());
    }

    @Test void calibrationSupportsTargetsAndRejectsTyposOrReservedName() throws Exception {
        var path = temporary.resolve("candidate.properties");
        var original = WorkloadGenerationConfig.defaults(WorkloadScenario.BALANCED);
        Files.writeString(path, "name=longer-targets\nINTERACTIVE.slaSeconds=15\nAI_INFERENCE.slaSeconds=50\n");
        var candidate = Calibration.load(path, original);
        assertEquals(15, candidate.config().profiles().get(WorkloadClass.INTERACTIVE).responseTimeTargetSeconds().orElseThrow());
        Files.writeString(path, "name=original-stress\n");
        assertThrows(IllegalArgumentException.class, () -> Calibration.load(path, original));
        Files.writeString(path, "name=typo\nINTERACTIVE.slaSecond=15\n");
        assertThrows(IllegalArgumentException.class, () -> Calibration.load(path, original));
    }

    @Test void stockRunsPreserveEpochAndFreshCloudletsAndExposeMissingDispatch() {
        var base = WorkloadGenerationConfig.defaults(WorkloadScenario.BALANCED);
        var trace = new WorkloadGenerator().generate(new WorkloadGenerationConfig(base.scenario(), 6, base.seed(),
            base.arrivalHorizonSeconds(), base.arrivalRatePerSecond(), base.profiles()));
        var metadata = new ExperimentRunner.Metadata("original-stress", ExperimentRunner.Purpose.ORIGINAL_STRESS,
            "cloudsim-stock-space-shared", trace, SimulationConfig.research(trace));
        var runner = new ExperimentRunner(); var first = runner.runStock(metadata); var second = runner.runStock(metadata);
        assertEquals(first.metrics(), second.metrics()); assertEquals(first.run().observations(), second.run().observations());
        assertEquals(trace, first.metadata().trace());
        for (int i = 0; i < trace.tasks().size(); i++) {
            var a = first.run().completed().get(i); var b = second.run().completed().get(i);
            assertNotSame(a.cloudlet(), b.cloudlet()); assertNotSame(a.cloudlet().getBroker(), b.cloudlet().getBroker());
            var o = first.run().observations().get(i);
            assertEquals(first.run().workloadEpoch() + o.task().arrivalTimeSeconds(), o.workloadArrival());
            assertEquals(a.cloudlet().getFinishTime() - o.workloadArrival(), o.responseTime().orElseThrow());
            assertEquals(first.run().workloadEpoch(), o.cloudSimSubmission().orElseThrow());
            assertTrue(o.readyQueueEntry().isEmpty()); assertTrue(o.dispatch().isEmpty()); assertTrue(o.queueWaitTime().isEmpty());
        }
    }

    @Test void actualTwoPeServiceMatchesPerPeLowerBoundAndSmokeTimesArePreserved() {
        var run = new SimulationController().run(SimulationConfig.milestoneOne());
        assertEquals(20.21, run.get(0).cloudlet().getFinishTime(), .001);
        assertEquals(1.1, run.get(1).cloudlet().getFinishTime(), .01);
        assertEquals(6.21, run.get(2).cloudlet().getFinishTime(), .001);
        for (var result : run) {
            double ideal = analyzer.idealServiceSeconds(result.metadata(), List.of(SimulationConfig.milestoneOne().vm()));
            double measured = result.cloudlet().getFinishTime() - result.cloudlet().getStartTime();
            assertTrue(measured >= ideal - 1e-8);
            assertTrue(measured <= ideal + .111, "CloudSim event granularity may add 0.11 s; ideal is a lower bound");
        }
    }
}
