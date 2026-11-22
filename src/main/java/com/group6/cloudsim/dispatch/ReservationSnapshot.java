package com.group6.cloudsim.dispatch;

import com.group6.cloudsim.workload.WorkloadClass;
import java.util.Map;

/** Instantaneous reserved resources and schedulable capacity across the active VM pool. */
public record ReservationSnapshot(Resources capacity, Map<WorkloadClass, Resources> byClass) {
    static final Resources ZERO = new Resources(0, 0);

    public ReservationSnapshot {
        if (capacity == null || capacity.pes() == 0 || capacity.ramMb() == 0)
            throw new IllegalArgumentException("Current VM capacity must include positive schedulable PEs and RAM");
        byClass = Map.copyOf(byClass);
        var totalReserved = byClass.values().stream().reduce(ZERO, Resources::plus);
        if (totalReserved.pes() > capacity.pes() || totalReserved.ramMb() > capacity.ramMb())
            throw new IllegalArgumentException("Reservations exceed current schedulable VM capacity");
    }

    public Resources reserved(WorkloadClass type) { return byClass.getOrDefault(type, ZERO); }

    public double dominantShare(WorkloadClass type) {
        var allocation = reserved(type);
        return Math.max((double) allocation.pes() / capacity.pes(), (double) allocation.ramMb() / capacity.ramMb());
    }

    public record Resources(long pes, long ramMb) {
        public Resources {
            if (pes < 0 || ramMb < 0) throw new IllegalArgumentException("Resource quantities must be nonnegative");
        }

        Resources plus(Resources other) {
            try {
                return new Resources(Math.addExact(pes, other.pes), Math.addExact(ramMb, other.ramMb));
            } catch (ArithmeticException e) {
                throw new IllegalStateException("VM resource totals exceed the supported 64-bit range", e);
            }
        }
    }
}
