package com.group6.cloudsim.dispatch;

import com.group6.cloudsim.workload.TaskMetadata;
import com.group6.cloudsim.workload.WorkloadClass;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.cloudsimplus.vms.Vm;

/** Both resources are committed in one simulation-thread operation. */
final class VmReservations {
    private final Map<Long, Slot> slots = new LinkedHashMap<>();
    private final Map<Long, Allocation> active = new LinkedHashMap<>();

    VmReservations(List<Vm> vms) {
        for (var vm : vms) slots.put(vm.getId(), new Slot(vm));
    }

    List<DispatchPolicy.VmCapacity> snapshot() {
        return slots.values().stream().filter(slot -> slot.vm.isCreated())
            .map(slot -> new DispatchPolicy.VmCapacity(slot.vm.getId(), slot.freePes, slot.freeRam)).toList();
    }

    ReservationSnapshot currentAllocations() {
        var capacity = ReservationSnapshot.ZERO;
        for (var slot : slots.values()) {
            if (slot.vm.isCreated()) {
                capacity = capacity.plus(new ReservationSnapshot.Resources(slot.vm.getPesNumber(), slot.vm.getRam().getCapacity()));
            }
        }
        var byClass = new EnumMap<WorkloadClass, ReservationSnapshot.Resources>(WorkloadClass.class);
        for (var allocation : active.values()) {
            if (!allocation.slot.vm.isCreated())
                throw new IllegalStateException("Active reservation on unavailable VM " + allocation.slot.vm.getId());
            var task = allocation.task;
            byClass.merge(task.workloadClass(), new ReservationSnapshot.Resources(task.pes(), task.ramDemandMb()),
                ReservationSnapshot.Resources::plus);
        }
        return new ReservationSnapshot(capacity, byClass);
    }

    Vm reserve(long vmId, TaskMetadata task) {
        var slot = slots.get(vmId);
        if (slot == null || !slot.vm.isCreated() || slot.freePes < task.pes()
            || slot.freeRam < task.ramDemandMb() || active.containsKey(task.id()))
            throw new IllegalStateException("Cannot reserve VM " + vmId + " for task " + task.id());
        active.put(task.id(), new Allocation(slot, task));
        slot.freePes -= task.pes();
        slot.freeRam -= task.ramDemandMb();
        return slot.vm;
    }

    boolean release(long taskId) {
        var allocation = active.remove(taskId);
        if (allocation == null) return false;
        allocation.slot.freePes += allocation.task.pes();
        allocation.slot.freeRam += allocation.task.ramDemandMb();
        return true;
    }

    private static final class Slot {
        private final Vm vm;
        private long freePes;
        private long freeRam;

        private Slot(Vm vm) {
            this.vm = vm;
            freePes = vm.getPesNumber();
            freeRam = vm.getRam().getCapacity();
        }
    }

    private record Allocation(Slot slot, TaskMetadata task) {}
}
