package com.group6.cloudsim.dispatch;

import com.group6.cloudsim.core.SimulationConfig;
import com.group6.cloudsim.core.SimulationController;
import com.group6.cloudsim.datacenter.DatacenterFactory;
import com.group6.cloudsim.evaluation.ExperimentRunner;
import com.group6.cloudsim.evaluation.SlaFeasibilityAnalyzer;
import com.group6.cloudsim.evaluation.TaskObservation;
import com.group6.cloudsim.workload.CloudletFactory;
import com.group6.cloudsim.workload.TaskMetadata;
import com.group6.cloudsim.workload.WorkloadGenerationConfig;
import com.group6.cloudsim.workload.WorkloadGenerator;
import com.group6.cloudsim.workload.WorkloadScenario;
import java.util.ArrayList;
import java.util.List;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.core.CloudSimTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.group6.cloudsim.dispatch.ReservationSnapshot.Resources;
import static com.group6.cloudsim.dispatch.StrictPriorityPolicyTest.task;
import static com.group6.cloudsim.workload.WorkloadClass.*;
import static org.junit.jupiter.api.Assertions.*;

class WeightedDrfIntegrationTest {
    private static final double EPS = 1e-8;
    private static final WmrfLgConfig REFERENCE = WmrfLgConfig.defaults();
    private static final SimulationConfig.MachineConfig HOST = new SimulationConfig.MachineConfig(6, 1000, 16384, 10000, 100000);
    private static final SimulationConfig.MachineConfig VM = new SimulationConfig.MachineConfig(2, 1000, 4096, 1000, 20000);
    private final WeightedDrfPolicy policy = new WeightedDrfPolicy(REFERENCE);

    @Test void eachAdmissionRefreshesSharesDuringTheSameDispatchEvent() {
        var tasks = List.of(task(0, AI_INFERENCE, 0, 1, 2500, 1000), task(1, AI_INFERENCE, 0, 1, 2500, 2000),
            task(2, BATCH, 0, 1, 2500, 5000), task(3, INTERACTIVE, 0, 1, 2500, 5000));
        var snapshots = new ArrayList<ReservationSnapshot>();
        var choices = new ArrayList<Long>();
        DispatchPolicy.WithCurrentAllocations observed = (now, ready, vms, snapshot) -> {
            snapshots.add(snapshot);
            var choice = policy.selectWithAllocations(now, ready, vms, snapshot);
            choice.ifPresent(decision -> choices.add(decision.taskId()));
            return choice;
        };
        var run = new SimulationController().runObserved(config(3, tasks), observed, REFERENCE.windowSeconds());
        assertEquals(List.of(0L, 2L, 3L, 1L), choices);
        assertEquals(new Resources(6, 12288), snapshots.get(0).capacity());
        assertTrue(snapshots.get(0).byClass().isEmpty());
        assertEquals(new Resources(1, 2500), snapshots.get(1).reserved(AI_INFERENCE));
        assertEquals(new Resources(1, 2500), snapshots.get(2).reserved(BATCH));
        assertEquals(new Resources(1, 2500), snapshots.get(3).reserved(INTERACTIVE));
        assertEquals(0, snapshots.get(4).dominantShare(AI_INFERENCE));
        assertEquals(run.observations().get(0).completion().orElseThrow(), run.observations().get(1).dispatch().orElseThrow(), EPS);
        for (var observation : run.observations()) assertEquals(0, observation.preExecutionDelay().orElseThrow(), EPS);
        assertEquals(4, run.completed().size());
    }

    @Test void sumsSameClassAcrossVmsAndExcludesWaitingAndCompletedTasks() {
        var tasks = List.of(task(0, BATCH, 0, 2, 2500, 1000), task(1, BATCH, 0, 2, 3500, 5000),
            task(2, AI_INFERENCE, .25, 1, 2500, 2000));
        var snapshots = new ArrayList<ReservationSnapshot>();
        DispatchPolicy.WithCurrentAllocations observed = (now, ready, vms, snapshot) -> {
            snapshots.add(snapshot);
            return policy.selectWithAllocations(now, ready, vms, snapshot);
        };
        var run = new SimulationController().runObserved(config(2, tasks), observed, REFERENCE.windowSeconds());
        assertEquals(new Resources(4, 8192), snapshots.get(2).capacity());
        assertEquals(new Resources(4, 6000), snapshots.get(2).reserved(BATCH));
        assertEquals(1, snapshots.get(2).dominantShare(BATCH));
        assertEquals(ReservationSnapshot.ZERO, snapshots.get(2).reserved(AI_INFERENCE));
        assertEquals(new Resources(2, 3500), snapshots.get(3).reserved(BATCH));
        assertEquals(.5, snapshots.get(3).dominantShare(BATCH));
        assertEquals(3, run.completed().size());
        assertEquals(run.observations().get(0).completion().orElseThrow(), run.observations().get(2).dispatch().orElseThrow(), EPS);
    }

    @Test void actualBypassKeepsOlderTaskQueuedAndWorkloadArrivalStillOrdersTheClass() {
        var tasks = List.of(task(0, BATCH, 0, 1, 2600, 6000),
            task(8, AI_INFERENCE, .5, 2, 2000, 1000), task(2, AI_INFERENCE, 1, 1, 1496, 1000),
            task(1, AI_INFERENCE, 1.5, 2, 2000, 1000));
        var run = new SimulationController().runObserved(config(1, tasks), policy, REFERENCE.windowSeconds());
        var old = run.observations().get(1);
        var bypassing = run.observations().get(2);
        var later = run.observations().get(3);
        assertEquals(4, run.completed().size());
        assertEquals(bypassing.workloadArrival(), bypassing.dispatch().orElseThrow(), EPS);
        assertEquals(old.workloadArrival(), old.readyQueueEntry().orElseThrow(), EPS);
        assertEquals(run.observations().get(0).completion().orElseThrow(), old.dispatch().orElseThrow(), EPS);
        assertEquals(old.completion().orElseThrow(), later.dispatch().orElseThrow(), EPS);
        double oldDcArrival = run.completed().stream().filter(c -> c.metadata().id() == 8).findFirst().orElseThrow()
            .cloudlet().getDcArrivalTime();
        assertEquals(old.dispatch().orElseThrow(), oldDcArrival, EPS);
        assertTrue(oldDcArrival > later.workloadArrival());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void failureAndCancellationRemoveCurrentReservations(boolean failure) {
        var tasks = List.of(task(0, AI_INFERENCE, 0, 2, 4096, 50000), task(1, BATCH, 0, 2, 4096, 2000));
        var config = config(1, tasks);
        var simulation = new CloudSimPlus();
        DatacenterFactory.createDatacenter(simulation, HOST);
        var cloudlets = tasks.stream().map(CloudletFactory::create).toList();
        var snapshots = new ArrayList<ReservationSnapshot>();
        DispatchPolicy.WithCurrentAllocations observed = (now, ready, vms, snapshot) -> {
            snapshots.add(snapshot);
            return policy.selectWithAllocations(now, ready, vms, snapshot);
        };
        var broker = new DispatchBroker(simulation, config, DatacenterFactory.createVms(VM, 1), cloudlets,
            new SlaFeasibilityAnalyzer().analyze(tasks, List.of(VM)).tasks(), observed, REFERENCE.windowSeconds());
        var stopped = cloudlets.get(0);
        stopped.addOnStartListener(event -> broker.schedule(2,
            failure ? CloudSimTag.CLOUDLET_FAIL : CloudSimTag.CLOUDLET_CANCEL, stopped));
        simulation.start();
        assertEquals(failure ? Cloudlet.Status.FAILED : Cloudlet.Status.CANCELED, stopped.getStatus());
        assertEquals(List.of(cloudlets.get(1)), broker.getCloudletFinishedList());
        assertEquals(stopped.getStartTime() + 2, cloudlets.get(1).getStartTime(), EPS);
        assertEquals(List.of(0.0, 1.0, 0.0), snapshots.stream().map(s -> s.dominantShare(AI_INFERENCE)).toList());
    }

    @Test void fourPoliciesUseOneImmutableTraceAndFreshCloudlets() {
        var trace = new WorkloadGenerator().generate(WorkloadGenerationConfig.defaults(WorkloadScenario.BALANCED));
        var infrastructure = SimulationConfig.research(trace);
        var purpose = ExperimentRunner.Purpose.ORIGINAL_STRESS;
        var runner = new ExperimentRunner();
        var weighted = runner.runWeightedDrf(new ExperimentRunner.Metadata("original-stress", purpose, WeightedDrfPolicy.ID, trace, infrastructure), REFERENCE);
        var fcfs = runner.runFcfs(new ExperimentRunner.Metadata("original-stress", purpose, FcfsPolicy.ID, trace, infrastructure), REFERENCE);
        var strict = runner.runStrictPriority(new ExperimentRunner.Metadata("original-stress", purpose, StrictPriorityPolicy.ID, trace, infrastructure), REFERENCE);
        var wmrf = runner.runWmrfLg(new ExperimentRunner.Metadata("original-stress", purpose, WmrfLgPolicy.ID, trace, infrastructure), REFERENCE);
        var identities = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Cloudlet, Boolean>());
        for (var result : List.of(weighted, fcfs, strict, wmrf)) {
            assertSame(trace, result.metadata().trace());
            assertSame(infrastructure, result.metadata().simulationConfig());
            assertEquals(trace.tasks(), result.run().observations().stream().map(TaskObservation::task).toList());
            assertEquals(weighted.feasibility(), result.feasibility());
            assertEquals(120, result.summary().aggregate().completed());
            assertTrue(result.fairness().isPresent());
            for (var completed : result.run().completed()) assertTrue(identities.add(completed.cloudlet()));
        }
        assertEquals(480, identities.size());
    }

    private static SimulationConfig config(int vmCount, List<TaskMetadata> tasks) {
        return new SimulationConfig(HOST, 1, VM, vmCount, tasks);
    }
}
