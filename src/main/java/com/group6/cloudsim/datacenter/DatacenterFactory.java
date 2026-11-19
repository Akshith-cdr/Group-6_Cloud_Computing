package com.group6.cloudsim.datacenter;

import com.group6.cloudsim.core.SimulationConfig.MachineConfig;
import java.util.List;
import java.util.stream.IntStream;
import org.cloudsimplus.core.Simulation;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.datacenters.DatacenterSimple;
import org.cloudsimplus.hosts.HostSimple;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.resources.PeSimple;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.schedulers.vm.VmSchedulerSpaceShared;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.vms.VmSimple;

/** Creates homogeneous infrastructure with explicit host CPU allocation and no CPU overcommit. */
public final class DatacenterFactory {
    private DatacenterFactory() {}

    public static Datacenter createDatacenter(Simulation simulation, MachineConfig host) {
        return createDatacenter(simulation, host, 1);
    }

    public static Datacenter createDatacenter(Simulation simulation, MachineConfig host, int hostCount) {
        if (hostCount <= 0) throw new IllegalArgumentException("Host count must be positive");
        List<Host> hosts = IntStream.range(0, hostCount).mapToObj(index -> createHost(host)).toList();
        return new DatacenterSimple(simulation, hosts);
    }

    private static Host createHost(MachineConfig host) {
        List<Pe> processingElements = IntStream.range(0, host.pes())
            .<Pe>mapToObj(index -> new PeSimple(host.mipsPerPe()))
            .toList();
        var physicalHost = new HostSimple(host.ramMb(), host.bandwidthMbps(), host.storageMb(), processingElements);
        physicalHost.setVmScheduler(new VmSchedulerSpaceShared());
        return physicalHost;
    }

    public static List<Vm> createVms(MachineConfig config, int vmCount) {
        if (vmCount <= 0) throw new IllegalArgumentException("VM count must be positive");
        return IntStream.range(0, vmCount).mapToObj(index -> createVm(config)).toList();
    }

    public static Vm createVm(MachineConfig config) {
        return new VmSimple(config.mipsPerPe(), config.pes())
            .setRam(config.ramMb())
            .setBw(config.bandwidthMbps())
            .setSize(config.storageMb())
            .setCloudletScheduler(new CloudletSchedulerSpaceShared());
    }
}
