package com.group6.cloudsim.core;

import com.group6.cloudsim.datacenter.DatacenterFactory;
import com.group6.cloudsim.workload.CloudletFactory;
import com.group6.cloudsim.workload.TaskMetadata;
import com.group6.cloudsim.evaluation.TaskObservation;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;

/** Owns the CloudSim lifecycle and links broker results to the original workload labels. */
public final class SimulationController {
    public List<CompletedTask> run(SimulationConfig config) {
        var result = runObserved(config);
        if (result.completed().size() != config.tasks().size())
            throw new IllegalStateException("Simulation did not successfully complete all configured tasks");
        return result.completed();
    }

    /** Observes unsuccessful and unfinished tasks without turning them into completions. */
    public ObservedRun runObserved(SimulationConfig config) {
        var tasksById = config.tasks().stream()
            .collect(Collectors.toMap(TaskMetadata::id, Function.identity()));
        var simulation = new CloudSimPlus();
        DatacenterFactory.createDatacenter(simulation, config.host(), config.hostCount());
        var broker = new DatacenterBrokerSimple(simulation);
        broker.submitVmList(DatacenterFactory.createVms(config.vm(), config.vmCount()));
        // Arrivals use a workload clock whose zero is readiness of ALL VMs, not simulator zero.
        // Submit in that callback so per-Cloudlet delays have one measured, shared origin.
        var workloadEpoch = new double[] { Double.NaN };
        var cloudlets = config.tasks().stream().map(CloudletFactory::create).toList();
        broker.addOnVmsCreatedListener(event -> {
            if (Double.isFinite(workloadEpoch[0])) return;
            if (broker.getVmCreatedList().size() != config.vmCount())
                throw new IllegalStateException("Not all configured VMs were created");
            workloadEpoch[0] = simulation.clock();
            broker.submitCloudletList(new ArrayList<>(cloudlets));
        });
        simulation.start();

        List<Cloudlet> finished = broker.getCloudletFinishedList();
        var completed = finished.stream().filter(c -> c.getStatus() == Cloudlet.Status.SUCCESS)
            .sorted(Comparator.comparingLong(Cloudlet::getId))
            .map(cloudlet -> new CompletedTask(tasksById.get(cloudlet.getId()), cloudlet, workloadEpoch[0]))
            .toList();
        var observations = cloudlets.stream().map(c -> {
            var task = tasksById.get(c.getId());
            var outcome = switch (c.getStatus()) {
                case SUCCESS -> TaskObservation.Outcome.COMPLETED;
                case FAILED, FAILED_RESOURCE_UNAVAILABLE -> TaskObservation.Outcome.FAILED;
                case CANCELED -> TaskObservation.Outcome.CANCELLED;
                default -> TaskObservation.Outcome.UNFINISHED;
            };
            // Stock broker has no arrival-driven ready queue or policy dispatch event.
            return new TaskObservation(task, outcome, workloadEpoch[0] + task.arrivalTimeSeconds(),
                OptionalDouble.empty(), OptionalDouble.empty(), OptionalDouble.of(workloadEpoch[0]),
                c.getStartTime() >= 0 ? OptionalDouble.of(c.getStartTime()) : OptionalDouble.empty(),
                outcome == TaskObservation.Outcome.COMPLETED ? OptionalDouble.of(c.getFinishTime()) : OptionalDouble.empty());
        }).toList();
        // Exclude CloudSim shutdown grace time from the completed-run throughput window.
        double end = observations.stream().allMatch(o -> o.outcome() == TaskObservation.Outcome.COMPLETED)
            ? observations.stream().mapToDouble(o -> o.completion().orElseThrow()).max().orElse(workloadEpoch[0])
            : simulation.clock();
        return new ObservedRun(completed, observations, workloadEpoch[0], end);
    }

    public record ObservedRun(List<CompletedTask> completed, List<TaskObservation> observations,
                              double workloadEpoch, double observationEnd) {
        public ObservedRun { completed = List.copyOf(completed); observations = List.copyOf(observations); }
    }

    /** A finished Cloudlet and its original workload profile. */
    public record CompletedTask(TaskMetadata metadata, Cloudlet cloudlet, double workloadEpochSeconds) {
        public double expectedDatacenterArrivalSeconds() {
            return workloadEpochSeconds + metadata.arrivalTimeSeconds();
        }
    }
}
