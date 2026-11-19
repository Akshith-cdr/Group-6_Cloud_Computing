package com.group6.cloudsim.core;

import static org.junit.jupiter.api.Assertions.*;

import com.group6.cloudsim.workload.*;
import java.util.List;
import java.util.OptionalDouble;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.utilizationmodels.UtilizationModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class WorkloadSimulationTest {
    @ParameterizedTest
    @EnumSource(WorkloadScenario.class)
    void executesAllGeneratedTasksWithExactDatacenterArrivalsAndMetadata(WorkloadScenario scenario) {
        var trace = new WorkloadGenerator().generate(WorkloadGenerationConfig.defaults(scenario));
        var results = new SimulationController().run(SimulationConfig.research(trace));
        assertEquals(120, results.size());
        assertEquals(trace.tasks(), results.stream().map(SimulationController.CompletedTask::metadata).toList());
        var broker = results.get(0).cloudlet().getBroker();
        assertEquals(6, broker.getVmCreatedList().size());
        assertEquals(3, broker.getVmCreatedList().stream().map(vm -> vm.getHost().getId()).distinct().count());
        double epoch = results.get(0).workloadEpochSeconds();
        assertTrue(epoch > 0, "VM creation introduces a real offset from simulator zero");
        for (var result : results) {
            var cloudlet = result.cloudlet();
            var task = result.metadata();
            assertEquals(epoch, result.workloadEpochSeconds());
            assertSame(trace.tasks().get((int) task.id()), task);
            assertEquals(task.id(), cloudlet.getId());
            assertEquals(task.lengthMiPerPe(), cloudlet.getLength());
            assertEquals(task.pes(), cloudlet.getPesNumber());
            assertEquals(task.arrivalTimeSeconds(), cloudlet.getSubmissionDelay());
            assertEquals(UtilizationModel.Unit.ABSOLUTE, cloudlet.getUtilizationModelRam().getUnit());
            assertEquals(task.ramDemandMb(), cloudlet.getUtilizationModelRam().getUtilization(0));
            assertEquals(result.expectedDatacenterArrivalSeconds(), cloudlet.getDcArrivalTime(), 1e-8);
            assertTrue(cloudlet.getStartTime() + 1e-8 >= result.expectedDatacenterArrivalSeconds());
            assertEquals(Cloudlet.Status.SUCCESS, cloudlet.getStatus());
        }
    }

    @Test
    void idleVmStartsAtItsArrivalIncludingZeroAndVerySmallDelays() {
        for (double arrival : new double[] {0, 0.00001, 0.05, 10, 100}) {
            var task = new TaskMetadata(0, WorkloadClass.INTERACTIVE, arrival, 2_000, 1, 512, OptionalDouble.of(5), 42);
            var smoke = SimulationConfig.milestoneOne();
            var result = new SimulationController().run(new SimulationConfig(smoke.host(), smoke.vm(), List.of(task))).get(0);
            assertEquals(result.expectedDatacenterArrivalSeconds(), result.cloudlet().getDcArrivalTime(), 1e-8);
            assertEquals(result.expectedDatacenterArrivalSeconds(), result.cloudlet().getStartTime(), 1e-8);
        }
    }

    @Test
    void contentionDelaysStartWithoutChangingArrival() {
        var tasks = List.of(
            new TaskMetadata(0, WorkloadClass.BATCH, 0, 40_000, 2, 1_024, OptionalDouble.empty(), 42),
            new TaskMetadata(1, WorkloadClass.INTERACTIVE, 0.5, 2_000, 1, 512, OptionalDouble.of(5), 42));
        var machine = new SimulationConfig.MachineConfig(2, 1_000, 4_096, 1_000, 20_000);
        var results = new SimulationController().run(new SimulationConfig(machine, machine, tasks));
        var queued = results.get(1);
        assertEquals(queued.expectedDatacenterArrivalSeconds(), queued.cloudlet().getDcArrivalTime(), 1e-8);
        assertTrue(queued.cloudlet().getStartTime() > queued.cloudlet().getDcArrivalTime() + 1);
    }

    @Test
    void pendingArrivalsSurviveLongIdleGaps() {
        var tasks = List.of(
            new TaskMetadata(7, WorkloadClass.INTERACTIVE, 0, 2_000, 1, 512, OptionalDouble.of(5), 42),
            new TaskMetadata(3, WorkloadClass.INTERACTIVE, 100, 2_000, 1, 512, OptionalDouble.of(5), 42),
            new TaskMetadata(11, WorkloadClass.INTERACTIVE, 1_000, 2_000, 1, 512, OptionalDouble.of(5), 42));
        var smoke = SimulationConfig.milestoneOne();
        var results = new SimulationController().run(new SimulationConfig(smoke.host(), smoke.vm(), tasks));

        assertEquals(3, results.size());
        for (var task : tasks) {
            var result = results.stream().filter(item -> item.metadata().id() == task.id()).findFirst().orElseThrow();
            assertSame(task, result.metadata());
            assertEquals(result.expectedDatacenterArrivalSeconds(), result.cloudlet().getDcArrivalTime(), 1e-8);
            assertEquals(result.expectedDatacenterArrivalSeconds(), result.cloudlet().getStartTime(), 1e-8);
            assertEquals(Cloudlet.Status.SUCCESS, result.cloudlet().getStatus());
        }
    }

    @Test
    void simultaneousArrivalsPreserveMetadataByIdAndLengthPerPe() {
        // Deliberately use IDs unrelated to list positions, with all three classes arriving together.
        var tasks = List.of(
            new TaskMetadata(90, WorkloadClass.BATCH, 2.5, 40_000, 1, 1_024, OptionalDouble.empty(), 42),
            new TaskMetadata(2, WorkloadClass.INTERACTIVE, 2.5, 2_000, 1, 512, OptionalDouble.of(5), 42),
            new TaskMetadata(40, WorkloadClass.AI_INFERENCE, 2.5, 12_000, 2, 1_536, OptionalDouble.of(10), 42));
        var smoke = SimulationConfig.milestoneOne();
        var results = new SimulationController().run(new SimulationConfig(smoke.host(), smoke.vm(), tasks));

        assertEquals(List.of(2L, 40L, 90L), results.stream().map(result -> result.metadata().id()).toList());
        for (var result : results) {
            var task = tasks.stream().filter(item -> item.id() == result.cloudlet().getId()).findFirst().orElseThrow();
            assertSame(task, result.metadata());
            assertEquals(result.expectedDatacenterArrivalSeconds(), result.cloudlet().getDcArrivalTime(), 1e-8);
            assertEquals(result.expectedDatacenterArrivalSeconds(), result.cloudlet().getStartTime(), 1e-8);
            assertEquals(task.lengthMiPerPe(), result.cloudlet().getLength());
            assertEquals(task.lengthMiPerPe() * task.pes(), result.cloudlet().getTotalLength());
        }
    }

    @Test
    void traceCanBeReusedForFreshCloudletsAndRepeatableExecution() {
        var trace = new WorkloadGenerator().generate(new WorkloadGenerationConfig(WorkloadScenario.BALANCED, 12, 42,
            600, 0.5, WorkloadGenerationConfig.defaultProfiles()));
        var controller = new SimulationController();
        var first = controller.run(SimulationConfig.research(trace));
        var second = controller.run(SimulationConfig.research(trace));
        for (int i = 0; i < first.size(); i++) {
            assertNotSame(first.get(i).cloudlet(), second.get(i).cloudlet());
            assertEquals(first.get(i).cloudlet().getDcArrivalTime(), second.get(i).cloudlet().getDcArrivalTime(), 1e-8);
            assertEquals(first.get(i).cloudlet().getFinishTime(), second.get(i).cloudlet().getFinishTime(), 1e-8);
        }
    }
}
