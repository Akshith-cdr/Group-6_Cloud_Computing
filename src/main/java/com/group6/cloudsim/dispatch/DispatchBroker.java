package com.group6.cloudsim.dispatch;

import com.group6.cloudsim.core.SimulationConfig;
import com.group6.cloudsim.evaluation.SlaFeasibilityAnalyzer.TaskFeasibility;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.cloudsimplus.brokers.DatacenterBrokerSimple;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.core.CloudSimPlus;
import org.cloudsimplus.core.CloudSimTag;
import org.cloudsimplus.core.events.SimEvent;
import org.cloudsimplus.vms.Vm;

/** Owns arrival, admission and release events. Physical VM placement uses the stock broker path. */
public final class DispatchBroker extends DatacenterBrokerSimple {
    private static final int ARRIVAL = 10_601;
    private static final int DISPATCH = 10_602;
    private final DispatchPolicy policy;
    private final ResourceTimeAccountant service;
    private final ArrayDeque<TaskFeasibility> pending;
    private final Map<Long, TaskFeasibility> ready = new LinkedHashMap<>();
    private final Map<Long, Cloudlet> cloudlets = new HashMap<>();
    private final Map<Long, Double> readyTimes = new HashMap<>();
    private final Map<Long, Double> dispatchTimes = new HashMap<>();
    private VmReservations reservations;
    private double epoch;
    private double latestArrival;
    private double lastRelease;
    private boolean dispatchPending;

    public DispatchBroker(CloudSimPlus simulation, SimulationConfig config, List<Vm> vms,
                          List<Cloudlet> cloudletList, List<TaskFeasibility> tasks,
                          DispatchPolicy policy, double window) {
        super(simulation);
        this.policy = policy;
        service = new ResourceTimeAccountant((double) config.vmCount() * config.vm().pes(),
            (double) config.vmCount() * config.vm().ramMb(), window);
        pending = new ArrayDeque<>(tasks);
        for (var cloudlet : cloudletList) cloudlets.put(cloudlet.getId(), cloudlet);
        for (var vm : vms) vm.setCloudletScheduler(new DispatchCloudletScheduler(this::release));
        setShutdownWhenIdle(false);
        addOnVmsCreatedListener(event -> {
            if (reservations != null) return;
            if (getVmCreatedList().size() != config.vmCount())
                throw new IllegalStateException("Not all configured VMs were created");
            reservations = new VmReservations(getVmCreatedList());
            epoch = simulation.clock();
            tasks.stream().mapToDouble(t -> t.task().arrivalTimeSeconds()).distinct()
                .forEach(arrival -> schedule(arrival, ARRIVAL));
        });
        submitVmList(vms);
    }

    @Override
    public void processEvent(SimEvent event) {
        switch (event.getTag()) {
            case ARRIVAL -> requestDispatch();
            case DISPATCH -> {
                dispatchPending = false;
                dispatchReady();
            }
            case CloudSimTag.CLOUDLET_RETURN -> {
                super.processEvent(event);
                release((Cloudlet) event.getData());
            }
            default -> super.processEvent(event);
        }
    }

    private void requestDispatch() {
        if (!dispatchPending) {
            dispatchPending = true;
            schedule(0, DISPATCH);
        }
    }

    private void dispatchReady() {
        double clock = getSimulation().clock();
        while (!pending.isEmpty() && epoch + pending.getFirst().task().arrivalTimeSeconds() <= clock) {
            var task = pending.removeFirst();
            latestArrival = task.task().arrivalTimeSeconds();
            ready.put(task.task().id(), task);
            readyTimes.put(task.task().id(), clock);
        }
        double now = workloadTime(clock);
        while (!ready.isEmpty()) {
            var candidates = List.copyOf(ready.values());
            var capacity = reservations.snapshot();
            var choice = policy instanceof DispatchPolicy.WithCurrentAllocations allocationPolicy
                ? allocationPolicy.selectWithAllocations(now, candidates, capacity, reservations.currentAllocations())
                : policy.select(now, candidates, capacity, service);
            if (choice.isEmpty()) return;
            var decision = choice.get();
            var task = ready.remove(decision.taskId());
            if (task == null) throw new IllegalStateException("Policy selected a task outside the ready queue");
            var vm = reservations.reserve(decision.vmId(), task.task());
            service.start(task.task(), now);
            var cloudlet = cloudlets.get(decision.taskId());
            dispatchTimes.put(cloudlet.getId(), clock);
            cloudlet.setSubmissionDelay(0);
            submitCloudletList(List.of(cloudlet), vm);
        }
    }

    private void release(Cloudlet cloudlet) {
        if (!reservations.release(cloudlet.getId())) return;
        lastRelease = getSimulation().clock();
        service.finish(cloudlet.getId(), workloadTime(lastRelease));
        requestDispatch();
    }

    private double workloadTime(double clock) {
        // Subtracting the epoch can lose an ulp at an exact trace-arrival boundary.
        return Math.max(latestArrival, clock - epoch);
    }

    public double lastRelease() { return lastRelease; }

    public OptionalDouble readyTime(long id) { return timestamp(readyTimes.get(id)); }

    public OptionalDouble dispatchTime(long id) { return timestamp(dispatchTimes.get(id)); }

    public Map<com.group6.cloudsim.workload.WorkloadClass, ResourceTimeAccountant.Usage> runShares(double end) {
        return service.runShares(workloadTime(end));
    }

    private static OptionalDouble timestamp(Double time) {
        return time == null ? OptionalDouble.empty() : OptionalDouble.of(time);
    }
}
