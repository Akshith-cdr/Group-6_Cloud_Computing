package com.group6.cloudsim.core;

import com.group6.cloudsim.datacenter.DatacenterFactory;
import com.group6.cloudsim.dispatch.DispatchBroker;
import com.group6.cloudsim.dispatch.DispatchPolicy;
import com.group6.cloudsim.dispatch.ResourceTimeAccountant;
import com.group6.cloudsim.evaluation.SlaFeasibilityAnalyzer;
import com.group6.cloudsim.workload.CloudletFactory;
import com.group6.cloudsim.workload.TaskMetadata;
import com.group6.cloudsim.workload.WorkloadClass;
import com.group6.cloudsim.evaluation.TaskObservation;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        return runObserved(config, null, 0);
    }

    public ObservedRun runObserved(SimulationConfig config, DispatchPolicy policy, double accountingWindow) {
        var tasksById = config.tasks().stream()
            .collect(Collectors.toMap(TaskMetadata::id, Function.identity()));
        var simulation = new CloudSimPlus();
        DatacenterFactory.createDatacenter(simulation, config.host(), config.hostCount());
        var vms = DatacenterFactory.createVms(config.vm(), config.vmCount());
        var cloudlets = config.tasks().stream().map(CloudletFactory::create).toList();
        var dispatch = policy == null ? null : new DispatchBroker(simulation, config, vms, cloudlets,
            new SlaFeasibilityAnalyzer().analyze(config.tasks(), List.of(config.vm())).tasks(), policy, accountingWindow);
        var broker = dispatch == null ? new DatacenterBrokerSimple(simulation) : dispatch;
        if (dispatch == null) broker.submitVmList(vms);
        // Arrivals use a workload clock whose zero is readiness of ALL VMs, not simulator zero.
        // Submit in that callback so per-Cloudlet delays have one measured, shared origin.
        var workloadEpoch = new double[] { Double.NaN };
        broker.addOnVmsCreatedListener(event -> {
            if (Double.isFinite(workloadEpoch[0])) return;
            if (broker.getVmCreatedList().size() != config.vmCount())
                throw new IllegalStateException("Not all configured VMs were created");
            workloadEpoch[0] = simulation.clock();
            if (dispatch == null) broker.submitCloudletList(new ArrayList<>(cloudlets));
        });
        simulation.start();
        if (!Double.isFinite(workloadEpoch[0])) throw new IllegalStateException("Configured VMs never became ready");

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
            var admission = dispatch == null ? OptionalDouble.empty() : dispatch.dispatchTime(c.getId());
            return new TaskObservation(task, outcome, workloadEpoch[0] + task.arrivalTimeSeconds(),
                dispatch == null ? OptionalDouble.empty() : dispatch.readyTime(c.getId()), admission,
                dispatch == null ? OptionalDouble.of(workloadEpoch[0]) : admission,
                c.getStartTime() >= 0 ? OptionalDouble.of(c.getStartTime()) : OptionalDouble.empty(),
                outcome == TaskObservation.Outcome.COMPLETED ? OptionalDouble.of(c.getFinishTime()) : OptionalDouble.empty());
        }).toList();
        // Exclude shutdown grace time from fully resolved runs.
        double end = simulation.clock();
        if (observations.stream().allMatch(o -> o.outcome() == TaskObservation.Outcome.COMPLETED))
            end = observations.stream().mapToDouble(o -> o.completion().orElseThrow()).max().orElse(workloadEpoch[0]);
        else if (dispatch != null && observations.stream().noneMatch(o -> o.outcome() == TaskObservation.Outcome.UNFINISHED))
            end = dispatch.lastRelease();
        var shares = dispatch == null ? Map.<WorkloadClass, ResourceTimeAccountant.Usage>of() : dispatch.runShares(end);
        return new ObservedRun(completed, observations, workloadEpoch[0], end, shares);
    }

    public record ObservedRun(List<CompletedTask> completed, List<TaskObservation> observations,
                              double workloadEpoch, double observationEnd,
                              Map<WorkloadClass, ResourceTimeAccountant.Usage> reservationShares) {
        public ObservedRun {
            completed = List.copyOf(completed);
            observations = List.copyOf(observations);
            reservationShares = Map.copyOf(reservationShares);
        }

        public ObservedRun(List<CompletedTask> completed, List<TaskObservation> observations,
                           double workloadEpoch, double observationEnd) {
            this(completed, observations, workloadEpoch, observationEnd, Map.of());
        }
    }

    /** A finished Cloudlet and its original workload profile. */
    public record CompletedTask(TaskMetadata metadata, Cloudlet cloudlet, double workloadEpochSeconds) {
        public double expectedDatacenterArrivalSeconds() {
            return workloadEpochSeconds + metadata.arrivalTimeSeconds();
        }
    }
}
