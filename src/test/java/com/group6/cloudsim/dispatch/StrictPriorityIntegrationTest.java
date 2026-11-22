package com.group6.cloudsim.dispatch;

import com.group6.cloudsim.core.SimulationConfig;
import com.group6.cloudsim.core.SimulationController;
import com.group6.cloudsim.datacenter.DatacenterFactory;
import com.group6.cloudsim.evaluation.ExperimentRunner;
import com.group6.cloudsim.evaluation.MetricsCollector;
import com.group6.cloudsim.evaluation.SlaFeasibilityAnalyzer;
import com.group6.cloudsim.evaluation.TaskObservation;
import com.group6.cloudsim.workload.CloudletFactory;
import com.group6.cloudsim.workload.TaskMetadata;
import com.group6.cloudsim.workload.WorkloadGenerationConfig;
import com.group6.cloudsim.workload.WorkloadGenerator;
import com.group6.cloudsim.workload.WorkloadScenario;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.core.CloudSimTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.group6.cloudsim.dispatch.StrictPriorityPolicyTest.task;
import static com.group6.cloudsim.workload.WorkloadClass.*;
import static org.junit.jupiter.api.Assertions.*;

class StrictPriorityIntegrationTest {
    private static final double EPS = 1e-8;
    private static final WmrfLgConfig REFERENCE = WmrfLgConfig.defaults();
    private static final SimulationConfig.MachineConfig VM = new SimulationConfig.MachineConfig(2, 1000, 4096, 1000, 20000);

    @Test void sustainedInteractiveArrivalsDeferBatchAcrossEightReleases() {
        var tasks = new ArrayList<TaskMetadata>();
        tasks.add(task(0, BATCH, 0, 2, 4096, 1000));
        for (int i = 0; i < 8; i++) tasks.add(task(i + 1, INTERACTIVE, i * .25, 2, 4096, 1000));
        var run = simulate(tasks);
        var batch = run.observations().get(0);
        double previous = run.workloadEpoch();
        for (var interactive : run.observations().subList(1, 9)) {
            assertEquals(previous, interactive.dispatch().orElseThrow(), EPS);
            assertTrue(interactive.workloadArrival() <= previous + EPS);
            previous = interactive.completion().orElseThrow();
        }
        assertEquals(previous, batch.dispatch().orElseThrow(), EPS);
        assertEquals(9, run.completed().size());
        double wait = batch.queueWaitTime().orElseThrow();
        assertTrue(wait >= 8 - EPS);
        var summary = new MetricsCollector().summarize(new SlaFeasibilityAnalyzer().analyze(tasks, List.of(VM)),
            run.observations(), run.workloadEpoch(), run.observationEnd());
        var batchMetrics = summary.byClass().get(BATCH);
        assertEquals(wait, batchMetrics.maximumQueueWait().orElseThrow(), EPS);
        assertEquals(wait, batchMetrics.averageQueueWait().orElseThrow(), EPS);
        assertEquals(1, batchMetrics.completed());
        assertEquals(0, batchMetrics.unfinished());
        System.out.printf(Locale.ROOT, "Controlled strict-priority batch wait: %.3f s%n", wait);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void realDispatchBypassesBlockedInteractiveThenUsesWorkloadArrivalOrder(boolean blockedByRam) {
        var tasks = List.of(task(0, BATCH, 0, 1, 2600, 6000),
            task(8, INTERACTIVE, 1, blockedByRam ? 1 : 2, blockedByRam ? 3000 : 1000, 2000),
            task(1, INTERACTIVE, 2, 2, 1000, 2000), task(3, AI_INFERENCE, 2, 1, 1496, 1000));
        var run = simulate(tasks);
        var blocker = run.observations().get(0);
        var older = run.observations().get(1);
        var younger = run.observations().get(2);
        var inference = run.observations().get(3);
        assertEquals(4, run.completed().size());
        assertEquals(older.workloadArrival(), older.readyQueueEntry().orElseThrow(), EPS);
        assertEquals(inference.workloadArrival(), inference.dispatch().orElseThrow(), EPS);
        assertEquals(blocker.completion().orElseThrow(), older.dispatch().orElseThrow(), EPS);
        assertEquals(older.completion().orElseThrow(), younger.dispatch().orElseThrow(), EPS);
        double olderDcArrival = run.completed().stream().filter(c -> c.metadata().id() == 8)
            .findFirst().orElseThrow().cloudlet().getDcArrivalTime();
        assertEquals(older.dispatch().orElseThrow(), olderDcArrival, EPS);
        assertTrue(olderDcArrival > younger.workloadArrival());
        for (var observation : run.observations()) assertEquals(0, observation.preExecutionDelay().orElseThrow(), EPS);
    }

    @Test void rejectedReservationsLeaveBothResourceCountersAndTaskIdsUnchanged() {
        var simulation = new CloudSimPlus();
        DatacenterFactory.createDatacenter(simulation, VM);
        var broker = new DatacenterBrokerSimple(simulation);
        broker.submitVmList(DatacenterFactory.createVms(VM, 1));
        var exercised = new java.util.concurrent.atomic.AtomicBoolean();
        broker.addOnVmsCreatedListener(event -> {
            var ledger = new VmReservations(broker.getVmCreatedList());
            var original = ledger.snapshot();
            long vmId = original.get(0).id();
            var holder = task(0, BATCH, 0, 1, 2600, 1000);
            var peBlocked = task(1, INTERACTIVE, 0, 2, 1000, 1000);
            var ramBlocked = task(2, INTERACTIVE, 0, 1, 1497, 1000);
            ledger.reserve(vmId, holder);
            var held = ledger.snapshot();
            assertEquals(List.of(new DispatchPolicy.VmCapacity(vmId, 1, 1496)), held);
            for (var blocked : List.of(peBlocked, ramBlocked)) {
                assertThrows(IllegalStateException.class, () -> ledger.reserve(vmId, blocked));
                assertEquals(held, ledger.snapshot());
                assertFalse(ledger.release(blocked.id()));
            }
            assertTrue(ledger.release(holder.id()));
            for (var blocked : List.of(peBlocked, ramBlocked)) {
                ledger.reserve(vmId, blocked);
                assertTrue(ledger.release(blocked.id()));
            }
            assertEquals(original, ledger.snapshot());
            exercised.set(true);
        });
        simulation.start();
        assertTrue(exercised.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void failureAndCancellationReleaseBothResourcesAndReconsiderWaitingWork(boolean fail) {
        var config = config(List.of(task(0, INTERACTIVE, 0, 2, 4096, 50000), task(1, BATCH, 0, 2, 4096, 2000)));
        var simulation = new CloudSimPlus();
        DatacenterFactory.createDatacenter(simulation, VM);
        var cloudlets = config.tasks().stream().map(CloudletFactory::create).toList();
        var broker = new DispatchBroker(simulation, config, DatacenterFactory.createVms(VM, 1), cloudlets,
            new SlaFeasibilityAnalyzer().analyze(config.tasks(), List.of(VM)).tasks(),
            new StrictPriorityPolicy(), REFERENCE.windowSeconds());
        var stopped = cloudlets.get(0);
        stopped.addOnStartListener(event -> broker.schedule(2,
            fail ? CloudSimTag.CLOUDLET_FAIL : CloudSimTag.CLOUDLET_CANCEL, stopped));
        simulation.start();
        assertEquals(fail ? Cloudlet.Status.FAILED : Cloudlet.Status.CANCELED, stopped.getStatus());
        var waiting = cloudlets.get(1);
        assertEquals(List.of(waiting), broker.getCloudletFinishedList());
        assertEquals(Cloudlet.Status.SUCCESS, waiting.getStatus());
        assertEquals(stopped.getStartTime() + 2, waiting.getStartTime(), EPS);
    }

    @Test void allPoliciesConsumeTheSameTraceWithFreshCloudlets() {
        var trace = new WorkloadGenerator().generate(WorkloadGenerationConfig.defaults(WorkloadScenario.BALANCED));
        var config = SimulationConfig.research(trace);
        var purpose = ExperimentRunner.Purpose.ORIGINAL_STRESS;
        var runner = new ExperimentRunner();
        var strictMetadata = new ExperimentRunner.Metadata("original-stress", purpose, StrictPriorityPolicy.ID, trace, config);
        var strict = runner.runStrictPriority(strictMetadata, REFERENCE);
        var fcfs = runner.runFcfs(new ExperimentRunner.Metadata("original-stress", purpose, FcfsPolicy.ID, trace, config), REFERENCE);
        var wmrf = runner.runWmrfLg(new ExperimentRunner.Metadata("original-stress", purpose, WmrfLgPolicy.ID, trace, config), REFERENCE);
        for (var result : List.of(strict, fcfs, wmrf)) {
            assertSame(trace, result.metadata().trace());
            assertSame(config, result.metadata().simulationConfig());
            assertEquals(trace.tasks(), result.run().observations().stream().map(TaskObservation::task).toList());
            assertEquals(strict.feasibility(), result.feasibility());
            assertEquals(120, result.summary().aggregate().completed());
            assertTrue(result.fairness().isPresent());
        }
        for (int i = 0; i < trace.tasks().size(); i++) {
            assertNotSame(strict.run().completed().get(i).cloudlet(), fcfs.run().completed().get(i).cloudlet());
            assertNotSame(strict.run().completed().get(i).cloudlet(), wmrf.run().completed().get(i).cloudlet());
            assertNotSame(fcfs.run().completed().get(i).cloudlet(), wmrf.run().completed().get(i).cloudlet());
        }
        assertThrows(IllegalArgumentException.class, () -> runner.runStrictPriority(fcfs.metadata(), REFERENCE));
    }

    private static SimulationController.ObservedRun simulate(List<TaskMetadata> tasks) {
        return new SimulationController().runObserved(config(tasks), new StrictPriorityPolicy(), REFERENCE.windowSeconds());
    }

    private static SimulationConfig config(List<TaskMetadata> tasks) { return new SimulationConfig(VM, VM, tasks); }
}
