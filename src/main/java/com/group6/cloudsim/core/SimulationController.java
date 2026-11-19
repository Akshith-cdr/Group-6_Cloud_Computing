package com.group6.cloudsim.core;

import com.group6.cloudsim.datacenter.DatacenterFactory;
import com.group6.cloudsim.workload.CloudletFactory;
import com.group6.cloudsim.workload.TaskMetadata;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;

/** Owns the CloudSim lifecycle and links broker results to the original workload labels. */
public final class SimulationController {
    public List<CompletedTask> run(SimulationConfig config) {
        var tasksById = config.tasks().stream()
            .collect(Collectors.toMap(TaskMetadata::id, Function.identity()));
        var simulation = new CloudSimPlus();
        DatacenterFactory.createDatacenter(simulation, config.host(), config.hostCount());
        var broker = new DatacenterBrokerSimple(simulation);
        broker.submitVmList(DatacenterFactory.createVms(config.vm(), config.vmCount()));
        // Arrivals use a workload clock whose zero is readiness of ALL VMs, not simulator zero.
        // Submit in that callback so per-Cloudlet delays have one measured, shared origin.
        var workloadEpoch = new double[] { Double.NaN };
        broker.addOnVmsCreatedListener(event -> {
            if (Double.isFinite(workloadEpoch[0])) return;
            if (broker.getVmCreatedList().size() != config.vmCount())
                throw new IllegalStateException("Not all configured VMs were created");
            workloadEpoch[0] = simulation.clock();
            broker.submitCloudletList(new ArrayList<>(config.tasks().stream().map(CloudletFactory::create).toList()));
        });
        simulation.start();

        List<Cloudlet> finished = broker.getCloudletFinishedList();
        long successful = finished.stream().filter(cloudlet -> cloudlet.getStatus() == Cloudlet.Status.SUCCESS).count();
        if (successful != config.tasks().size()) {
            throw new IllegalStateException(
                "Simulation completed " + successful + " of " + config.tasks().size() + " cloudlets successfully."
            );
        }

        return finished.stream()
            .sorted(Comparator.comparingLong(Cloudlet::getId))
            .map(cloudlet -> new CompletedTask(tasksById.get(cloudlet.getId()), cloudlet, workloadEpoch[0]))
            .toList();
    }

    /** A finished Cloudlet and its original workload profile. */
    public record CompletedTask(TaskMetadata metadata, Cloudlet cloudlet, double workloadEpochSeconds) {
        public double expectedDatacenterArrivalSeconds() {
            return workloadEpochSeconds + metadata.arrivalTimeSeconds();
        }
    }
}
