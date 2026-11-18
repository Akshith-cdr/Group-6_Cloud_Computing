package com.group6.cloudsim.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.group6.cloudsim.core.SimulationController.CompletedTask;
import com.group6.cloudsim.workload.TaskMetadata;
import com.group6.cloudsim.workload.WorkloadClass;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.stream.Collectors;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.junit.jupiter.api.Test;

class SimulationControllerTest {
    @Test
    void submitsAndCompletesAllThreeWorkloadClassesOnTheCreatedVm() {
        var config = SimulationConfig.milestoneOne();
        var results = new SimulationController().run(config);

        assertEquals(3, results.size());
        assertEquals(config.tasks(), results.stream().map(CompletedTask::metadata).toList());
        var classes = results.stream()
            .map(result -> result.metadata().workloadClass())
            .collect(Collectors.toSet());
        assertEquals(EnumSet.allOf(WorkloadClass.class), classes);

        var cloudlets = results.stream().map(CompletedTask::cloudlet).toList();
        var broker = cloudlets.get(0).getBroker();
        var simulation = assertInstanceOf(CloudSimPlus.class, broker.getSimulation());
        assertEquals(config.tasks().stream().map(TaskMetadata::id).toList(),
            broker.getCloudletSubmittedList().stream().map(Cloudlet::getId).toList());
        assertEquals(cloudlets, broker.getCloudletFinishedList().stream()
            .sorted(Comparator.comparingLong(Cloudlet::getId)).toList());
        assertEquals(1, broker.getVmCreatedList().size());
        var vm = broker.getVmCreatedList().get(0);

        for (var result : results) {
            var cloudlet = result.cloudlet();
            var task = result.metadata();
            assertSame(vm, cloudlet.getVm());
            assertEquals(Cloudlet.Status.SUCCESS, cloudlet.getStatus());
            assertEquals(task.id(), cloudlet.getId());
            assertEquals(task.lengthMiPerPe(), cloudlet.getLength());
            assertEquals(task.pes(), cloudlet.getPesNumber());
            assertEquals(task.ramFraction(), cloudlet.getUtilizationModelRam().getUtilization(0));
            assertTrue(cloudlet.getStartTime() >= 0);
            assertTrue(cloudlet.getFinishTime() > cloudlet.getStartTime());
            assertTrue(simulation.clock() >= cloudlet.getFinishTime());
        }

        double lastStart = cloudlets.stream().mapToDouble(Cloudlet::getStartTime).max().orElseThrow();
        double firstFinish = cloudlets.stream().mapToDouble(Cloudlet::getFinishTime).min().orElseThrow();
        assertTrue(lastStart < firstFinish, "The three workload classes must overlap in the demo.");
    }
}
