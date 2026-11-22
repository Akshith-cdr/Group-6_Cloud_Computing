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
import java.util.ArrayList;
import java.util.List;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.core.CloudSimTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.group6.cloudsim.dispatch.WmrfLgPolicyTest.task;
import static com.group6.cloudsim.workload.WorkloadClass.*;
import static org.junit.jupiter.api.Assertions.*;

class DispatchIntegrationTest {
    private static final double EPS = 1e-8;
    private final WmrfLgPolicy policy = new WmrfLgPolicy(WmrfLgConfig.defaults());

    @Test void arrivalVisibilityIncludesSubTickAndLongIdleGaps() {
        var tasks = List.of(task(5, BATCH, 0, 1, 500, 1000, 0), task(2, BATCH, .00001, 1, 500, 1000, 0),
            task(1, BATCH, 100, 1, 500, 1000, 0));
        var seen = new ArrayList<Long>();
        DispatchPolicy observing = (now, ready, vms, service) -> {
            for (var item : ready) {
                assertTrue(item.task().arrivalTimeSeconds() <= now);
                seen.add(item.task().id());
            }
            return policy.select(now, ready, vms, service);
        };
        var run = new SimulationController().runObserved(config(tasks), observing, 60);
        assertEquals(List.of(5L, 2L, 1L), seen);
        for (var observation : run.observations()) {
            assertEquals(observation.workloadArrival(), observation.readyQueueEntry().orElseThrow(), EPS);
            assertEquals(observation.workloadArrival(), observation.dispatch().orElseThrow(), EPS);
            assertEquals(0, observation.preExecutionDelay().orElseThrow(), EPS);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void reservationsBlockAdmissionUntilCompletion(boolean ramBottleneck) {
        int pes = ramBottleneck ? 1 : 2;
        int ram = ramBottleneck ? 3000 : 500;
        var tasks = List.of(task(0, BATCH, 0, pes, ram, 6000, 0), task(1, BATCH, 0, pes, ram, 2000, 0));
        var run = new SimulationController().runObserved(config(tasks), policy, 60);
        assertEquals(2, run.completed().size());
        var first = run.observations().get(0);
        var second = run.observations().get(1);
        assertEquals(0, first.queueWaitTime().orElseThrow(), EPS);
        assertEquals(first.completion().orElseThrow(), second.dispatch().orElseThrow(), EPS);
        assertEquals(second.dispatch().orElseThrow(), run.completed().get(1).cloudlet().getDcArrivalTime(), EPS);
        assertTrue(second.queueWaitTime().orElseThrow() > 0);
        assertEquals(0, second.preExecutionDelay().orElseThrow(), EPS);
        assertEquals((double) pes / 2, run.reservationShares().get(BATCH).cpu(), EPS);
        assertEquals((double) ram / 4096, run.reservationShares().get(BATCH).ram(), EPS);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void actualFailureAndCancellationFreeBothResourcesAndWakeWaitingTask(boolean failure) {
        var config = config(List.of(task(0, BATCH, 0, 2, 3500, 50000, 0),
            task(1, BATCH, 0, 2, 3500, 2000, 0)));
        var sim = new CloudSimPlus();
        DatacenterFactory.createDatacenter(sim, config.host());
        var cloudlets = config.tasks().stream().map(CloudletFactory::create).toList();
        var broker = new DispatchBroker(sim, config, DatacenterFactory.createVms(config.vm(), 1), cloudlets,
            new SlaFeasibilityAnalyzer().analyze(config.tasks(), List.of(config.vm())).tasks(), policy, 60);
        var first = cloudlets.get(0);
        first.addOnStartListener(event -> broker.schedule(2,
            failure ? CloudSimTag.CLOUDLET_FAIL : CloudSimTag.CLOUDLET_CANCEL, first));
        sim.start();
        assertEquals(failure ? Cloudlet.Status.FAILED : Cloudlet.Status.CANCELED, first.getStatus());
        var second = cloudlets.get(1);
        assertEquals(Cloudlet.Status.SUCCESS, second.getStatus());
        assertEquals(List.of(second), broker.getCloudletFinishedList());
        assertEquals(first.getStartTime() + 2, second.getStartTime(), EPS);
        assertEquals(0, second.getVm().getCloudletScheduler().getUsedPes());
    }

    @Test void simultaneousArrivalsCompeteAsOneReadySet() {
        var config = config(List.of(task(0, BATCH, 0, 2, 500, 6000, 0),
            task(1, INTERACTIVE, 0, 1, 500, 2000, 5)));
        var result = new SimulationController().runObserved(config, policy, 60);
        assertEquals(result.workloadEpoch(), result.observations().get(1).dispatch().orElseThrow(), EPS);
        assertTrue(result.observations().get(0).queueWaitTime().orElseThrow() > 0);
    }

    @Test void experimentRepeatsExactlyWithFreshCloudletsAndSharedUnmodifiedTrace() {
        var original = new WorkloadGenerator().generate(WorkloadGenerationConfig.defaults(WorkloadScenario.BALANCED));
        var config = SimulationConfig.research(original);
        var runner = new ExperimentRunner();
        var metadata = new ExperimentRunner.Metadata("original-stress", ExperimentRunner.Purpose.ORIGINAL_STRESS,
            WmrfLgPolicy.ID, original, config);
        var first = runner.runWmrfLg(metadata, WmrfLgConfig.defaults());
        var second = runner.runWmrfLg(metadata, WmrfLgConfig.defaults());
        var stock = runner.runStock(new ExperimentRunner.Metadata("original-stress", metadata.purpose(),
            ExperimentRunner.STOCK_POLICY, original, config));
        assertSame(original, first.metadata().trace());
        assertSame(original, stock.metadata().trace());
        assertEquals(new WorkloadGenerator().generate(original.config()), original);
        assertEquals(first.run().observations(), second.run().observations());
        assertEquals(first.summary(), second.summary());
        assertEquals(first.run().reservationShares(), second.run().reservationShares());
        assertEquals(first.fairness(), second.fairness());
        assertEquals(120, first.summary().aggregate().completed());
        assertEquals(first.run().observations().stream().map(o -> o.workloadArrival()).toList(),
            stock.run().observations().stream().map(o -> o.workloadArrival()).toList());
        for (int i = 0; i < original.tasks().size(); i++) {
            var cloudlet = first.run().completed().get(i).cloudlet();
            var replica = second.run().completed().get(i).cloudlet();
            assertNotSame(cloudlet, replica);
            assertEquals(cloudlet.getVm().getId(), replica.getVm().getId());
            assertEquals(cloudlet.getVm().getHost().getId(), replica.getVm().getHost().getId());
        }
    }

    private static SimulationConfig config(List<TaskMetadata> tasks) {
        var vm = new SimulationConfig.MachineConfig(2, 1000, 4096, 1000, 20000);
        return new SimulationConfig(vm, vm, tasks);
    }
}
