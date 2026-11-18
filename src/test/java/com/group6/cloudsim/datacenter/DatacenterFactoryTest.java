package com.group6.cloudsim.datacenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.group6.cloudsim.core.SimulationConfig.MachineConfig;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.junit.jupiter.api.Test;

class DatacenterFactoryTest {
    @Test
    void createsDatacenterWithConfiguredHostInTheSimulation() {
        var simulation = new CloudSimPlus();
        var config = new MachineConfig(6, 1_500, 8_192, 7_500, 500_000);
        var datacenter = DatacenterFactory.createDatacenter(simulation, config);

        assertSame(simulation, datacenter.getSimulation());
        assertEquals(1, datacenter.getHostList().size());
        var host = datacenter.getHostList().get(0);
        assertSame(datacenter, host.getDatacenter());
        assertEquals(config.pes(), host.getPesNumber());
        for (var pe : host.getPeList()) {
            assertEquals(config.mipsPerPe(), pe.getCapacity());
        }
        assertEquals(config.ramMb(), host.getRam().getCapacity());
        assertEquals(config.bandwidthMbps(), host.getBw().getCapacity());
        assertEquals(config.storageMb(), host.getStorage().getCapacity());
    }

    @Test
    void createsVmWithConfiguredResourcesAndSpaceSharedScheduler() {
        var config = new MachineConfig(2, 1_250, 2_048, 500, 10_000);
        var vm = DatacenterFactory.createVm(config);

        assertEquals(config.pes(), vm.getPesNumber());
        assertEquals(config.mipsPerPe(), vm.getMips());
        assertEquals(config.ramMb(), vm.getRam().getCapacity());
        assertEquals(config.bandwidthMbps(), vm.getBw().getCapacity());
        assertEquals(config.storageMb(), vm.getStorage().getCapacity());
        assertInstanceOf(CloudletSchedulerSpaceShared.class, vm.getCloudletScheduler());
    }
}
