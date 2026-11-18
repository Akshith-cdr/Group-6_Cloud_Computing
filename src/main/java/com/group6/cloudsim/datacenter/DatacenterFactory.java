package com.group6.cloudsim.datacenter;

import com.group6.cloudsim.core.SimulationConfig.MachineConfig;
import java.util.List;
import java.util.stream.IntStream;
import org.cloudsimplus.core.Simulation;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

/** Creates the single-host datacenter and VM used by the initial scenario. */
public final class DatacenterFactory {
    private DatacenterFactory() {}

    public static Datacenter createDatacenter(Simulation simulation, MachineConfig host) {
        List<Pe> processingElements = IntStream.range(0, host.pes())
            .<Pe>mapToObj(index -> new PeSimple(host.mipsPerPe()))
            .toList();
        var physicalHost = new HostSimple(host.ramMb(), host.bandwidthMbps(), host.storageMb(), processingElements);
        return new DatacenterSimple(simulation, List.of(physicalHost));
    }

    public static Vm createVm(MachineConfig config) {
        return new VmSimple(config.mipsPerPe(), config.pes())
            .setRam(config.ramMb())
            .setBw(config.bandwidthMbps())
            .setSize(config.storageMb())
            .setCloudletScheduler(new CloudletSchedulerSpaceShared());
    }
}
