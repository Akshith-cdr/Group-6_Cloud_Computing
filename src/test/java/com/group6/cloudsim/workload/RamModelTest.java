package com.group6.cloudsim.workload;

import static org.junit.jupiter.api.Assertions.*;

import com.group6.cloudsim.core.SimulationConfig.MachineConfig;
import com.group6.cloudsim.datacenter.DatacenterFactory;
import java.util.List;
import java.util.OptionalDouble;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.core.CloudSimPlus;
import org.junit.jupiter.api.Test;

class RamModelTest {
    @Test
    void schedulerAccountsForAbsoluteMbAsTheCorrectFractionOfVmCapacity() {
        var observation = observeMemory(1_536, 512, false);
        assertEquals(2, observation[0]);
        assertEquals(0.5, observation[1], 1e-9, "2048 MB on a 4096 MB VM must be 50%, not 2048%");
        assertEquals(2_048, observation[2]);
    }

    @Test
    void spaceSharedCpuSchedulingDoesNotProvideHardConcurrentRamReservations() {
        var observation = observeMemory(3_072, 3_072, true);
        assertEquals(2, observation[0], "Both tasks can enter execution despite aggregate RAM exceeding VM capacity");
        assertEquals(1.5, observation[1], 1e-9);
        assertTrue(observation[2] <= 4_096, "Physical allocated RAM is capped; requested demand is distinct");
    }

    /** Captures actual running-task counts, requested RAM fraction and allocated VM RAM during CloudSim execution. */
    private static double[] observeMemory(long firstRam, long secondRam, boolean stopEarly) {
        var simulation = new CloudSimPlus();
        var machine = new MachineConfig(2, 1_000, 4_096, 1_000, 20_000);
        DatacenterFactory.createDatacenter(simulation, machine).setSchedulingInterval(1);
        var broker = new DatacenterBrokerSimple(simulation);
        var vm = DatacenterFactory.createVm(machine);
        broker.submitVm(vm);
        var first = CloudletFactory.create(new TaskMetadata(0, WorkloadClass.BATCH, 0, 5_000, 1, firstRam, OptionalDouble.empty(), 42));
        var second = CloudletFactory.create(new TaskMetadata(1, WorkloadClass.BATCH, 0, 5_000, 1, secondRam, OptionalDouble.empty(), 42));
        broker.submitCloudletList(List.of(first, second));
        var peaks = new double[3];
        simulation.addOnEventProcessingListener(event -> {
            peaks[0] = Math.max(peaks[0], vm.getCloudletScheduler().getCloudletExecList().size());
            peaks[1] = Math.max(peaks[1], vm.getCloudletScheduler().getCurrentRequestedRamPercentUtilization());
            // VM creation initially allocates its full capacity; sample task usage after processing updates.
            if (simulation.clock() > 1.5 && !vm.getCloudletScheduler().getCloudletExecList().isEmpty())
                peaks[2] = Math.max(peaks[2], vm.getRam().getAllocatedResource());
        });
        if (stopEarly) simulation.terminateAt(2);
        simulation.start();
        if (!stopEarly) assertEquals(2, broker.getCloudletFinishedList().size());
        return peaks;
    }
}
