package com.group6.cloudsim.core;

import static org.junit.jupiter.api.Assertions.*;

import com.group6.cloudsim.core.SimulationConfig.MachineConfig;
import com.group6.cloudsim.datacenter.DatacenterFactory;
import com.group6.cloudsim.workload.*;
import java.util.List;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.schedulers.vm.VmSchedulerSpaceShared;
import org.junit.jupiter.api.Test;

class SimulationConfigTest {
    @Test
    void createsConfiguredResearchTopologyWithIndependentResources() {
        var trace = new WorkloadGenerator().generate(WorkloadGenerationConfig.defaults(WorkloadScenario.BALANCED));
        var config = SimulationConfig.research(trace);
        assertEquals(3, config.hostCount());
        assertEquals(6, config.vmCount());
        var dc = DatacenterFactory.createDatacenter(new CloudSimPlus(), config.host(), config.hostCount());
        assertEquals(12, dc.getHostList().stream().mapToLong(host -> host.getPesNumber()).sum());
        for (var host : dc.getHostList()) {
            assertEquals(16_384, host.getRam().getCapacity());
            assertInstanceOf(VmSchedulerSpaceShared.class, host.getVmScheduler());
        }
        assertNotSame(dc.getHost(0).getPeList().get(0), dc.getHost(1).getPeList().get(0));
        var vms = DatacenterFactory.createVms(config.vm(), 6);
        assertEquals(6, vms.size());
        assertNotSame(vms.get(0).getCloudletScheduler(), vms.get(1).getCloudletScheduler());
        assertTrue(vms.stream().allMatch(vm -> vm.getPesNumber() == 2 && vm.getRam().getCapacity() == 4_096));
    }

    @Test
    void rejectsAggregateFitsThatFailPerHostPackingAndOtherOvercommit() {
        var host = new MachineConfig(4, 1_000, 16_384, 10_000, 1_000_000);
        var vm = new MachineConfig(3, 1_000, 4_096, 1_000, 20_000);
        assertThrows(IllegalArgumentException.class, () -> new SimulationConfig(host, 3, vm, 4, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SimulationConfig(host, 3,
            new MachineConfig(1, 1_001, 1, 1, 1), 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SimulationConfig(host, 1,
            new MachineConfig(1, 1_000, 9_000, 1, 1), 2, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SimulationConfig(host, 1,
            new MachineConfig(1, 1_000, 1, 6_000, 1), 2, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SimulationConfig(host, 1,
            new MachineConfig(1, 1_000, 1, 1, 600_000), 2, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new SimulationConfig(host, 0, vm, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new MachineConfig(0, 1, 1, 1, 1));
    }

    @Test
    void allowsEmptyWorkloadAndCompletesIt() {
        var config = SimulationConfig.milestoneOne();
        assertTrue(new SimulationController().run(new SimulationConfig(config.host(), config.vm(), List.of())).isEmpty());
    }
}
