package com.group6.cloudsim.dispatch;

import com.group6.cloudsim.core.SimulationConfig;
import com.group6.cloudsim.core.SimulationController;
import com.group6.cloudsim.datacenter.DatacenterFactory;
import com.group6.cloudsim.evaluation.ExperimentRunner;
import com.group6.cloudsim.evaluation.SlaFeasibilityAnalyzer;
import com.group6.cloudsim.workload.CloudletFactory;
import com.group6.cloudsim.workload.TaskMetadata;
import com.group6.cloudsim.workload.WorkloadGenerationConfig;
import com.group6.cloudsim.workload.WorkloadGenerator;
import com.group6.cloudsim.workload.WorkloadScenario;
import java.util.List;
import java.util.OptionalDouble;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.core.CloudSimTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.group6.cloudsim.workload.WorkloadClass.BATCH;
import static org.junit.jupiter.api.Assertions.*;

class FcfsIntegrationTest {
    private static final double EPS = 1e-8;
    private static final WmrfLgConfig REFERENCE = WmrfLgConfig.defaults();

    @Test void simultaneousArrivalsUseIdAndAllTasksCompleteThroughDispatcher() {
        var config = config(List.of(task(8, 0, 2, 1000, 3000), task(3, 0, 2, 1000, 2000),
            task(0, 40, 2, 1000, 1000)));
        var run = new SimulationController().runObserved(config, new FcfsPolicy(), REFERENCE.windowSeconds());
        var observations = run.observations();
        assertEquals(3, run.completed().size());
        assertEquals(run.workloadEpoch(), observations.get(1).dispatch().orElseThrow(), EPS);
        assertEquals(observations.get(1).completion().orElseThrow(), observations.get(0).dispatch().orElseThrow(), EPS);
        assertEquals(run.workloadEpoch() + 40, observations.get(2).readyQueueEntry().orElseThrow(), EPS);
        assertEquals(run.workloadEpoch() + 40, observations.get(2).dispatch().orElseThrow(), EPS);
        assertEquals(observations, new SimulationController().runObserved(config, new FcfsPolicy(), REFERENCE.windowSeconds()).observations());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bypassKeepsOldTaskWaitingForTheMissingResource(boolean limitedByRam) {
        var config = config(List.of(task(0, 0, 1, 2600, 6000),
            task(1, 1, limitedByRam ? 1 : 2, limitedByRam ? 3000 : 1000, 2000),
            task(2, 2, 1, 1496, 1000)));
        var run = new SimulationController().runObserved(config, new FcfsPolicy(), REFERENCE.windowSeconds());
        var observations = run.observations();
        var blocker = observations.get(0);
        var old = observations.get(1);
        var later = observations.get(2);
        assertEquals(3, run.completed().size());
        assertEquals(old.workloadArrival(), old.readyQueueEntry().orElseThrow(), EPS);
        assertEquals(later.workloadArrival(), later.dispatch().orElseThrow(), EPS);
        assertEquals(blocker.completion().orElseThrow(), old.dispatch().orElseThrow(), EPS);
        assertTrue(later.completion().orElseThrow() < old.dispatch().orElseThrow());
        for (var observation : observations) assertEquals(0, observation.preExecutionDelay().orElseThrow(), EPS);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void terminalStopsReleaseBothResourcesForFcfs(boolean failure) {
        var config = config(List.of(task(0, 0, 2, 4096, 50000), task(1, 0, 2, 4096, 2000)));
        var simulation = new CloudSimPlus();
        DatacenterFactory.createDatacenter(simulation, config.host());
        var cloudlets = config.tasks().stream().map(CloudletFactory::create).toList();
        var broker = new DispatchBroker(simulation, config, DatacenterFactory.createVms(config.vm(), 1), cloudlets,
            new SlaFeasibilityAnalyzer().analyze(config.tasks(), List.of(config.vm())).tasks(),
            new FcfsPolicy(), REFERENCE.windowSeconds());
        var stopped = cloudlets.get(0);
        stopped.addOnStartListener(event -> broker.schedule(2,
            failure ? CloudSimTag.CLOUDLET_FAIL : CloudSimTag.CLOUDLET_CANCEL, stopped));
        simulation.start();
        assertEquals(failure ? Cloudlet.Status.FAILED : Cloudlet.Status.CANCELED, stopped.getStatus());
        var waiting = cloudlets.get(1);
        assertEquals(List.of(waiting), broker.getCloudletFinishedList());
        assertEquals(Cloudlet.Status.SUCCESS, waiting.getStatus());
        assertEquals(stopped.getStartTime() + 2, waiting.getStartTime(), EPS);
    }

    @Test void runnerUsesSameImmutableTraceAndFairnessReferenceForBothPolicies() {
        var trace = new WorkloadGenerator().generate(WorkloadGenerationConfig.defaults(WorkloadScenario.BALANCED));
        var config = SimulationConfig.research(trace);
        var purpose = ExperimentRunner.Purpose.ORIGINAL_STRESS;
        var runner = new ExperimentRunner();
        var fcfsMetadata = new ExperimentRunner.Metadata("original-stress", purpose, FcfsPolicy.ID, trace, config);
        var wmrfMetadata = new ExperimentRunner.Metadata("original-stress", purpose, WmrfLgPolicy.ID, trace, config);
        var fcfs = runner.runFcfs(fcfsMetadata, REFERENCE);
        var wmrf = runner.runWmrfLg(wmrfMetadata, REFERENCE);
        assertSame(trace, fcfs.metadata().trace());
        assertSame(trace, wmrf.metadata().trace());
        assertEquals(fcfs.feasibility(), wmrf.feasibility());
        assertNotSame(fcfs.run().completed().get(0).cloudlet(), wmrf.run().completed().get(0).cloudlet());
        assertEquals(REFERENCE, fcfs.referenceConfig().orElseThrow());
        assertEquals(REFERENCE, wmrf.referenceConfig().orElseThrow());
        assertTrue(fcfs.fairness().isPresent());
        assertEquals(120, fcfs.summary().aggregate().completed());
        assertEquals(120, wmrf.summary().aggregate().completed());
        assertThrows(IllegalArgumentException.class, () -> runner.runFcfs(wmrfMetadata, REFERENCE));
    }

    private static SimulationConfig config(List<TaskMetadata> tasks) {
        var vm = new SimulationConfig.MachineConfig(2, 1000, 4096, 1000, 20000);
        return new SimulationConfig(vm, vm, tasks);
    }

    private static TaskMetadata task(long id, double arrival, int pes, long ram, long length) {
        return new TaskMetadata(id, BATCH, arrival, length, pes, ram, OptionalDouble.empty(), 42);
    }
}
