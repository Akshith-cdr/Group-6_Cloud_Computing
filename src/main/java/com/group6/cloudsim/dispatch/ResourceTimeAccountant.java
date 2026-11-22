package com.group6.cloudsim.dispatch;

import com.group6.cloudsim.workload.TaskMetadata;
import com.group6.cloudsim.workload.WorkloadClass;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Run-local reservation occupancy. All timestamps are relative to VM readiness. */
public final class ResourceTimeAccountant {
    private final double totalPes;
    private final double totalRamMb;
    private final double window;
    private final Map<Long, Reservation> active = new LinkedHashMap<>();
    private final ArrayDeque<Completed> recent = new ArrayDeque<>();
    private final Map<WorkloadClass, Usage> finished = emptyUsage();
    private double lastEvent;

    ResourceTimeAccountant(double totalPes, double totalRamMb, double window) {
        if (!(totalPes > 0) || !Double.isFinite(totalPes) || !(totalRamMb > 0) || !Double.isFinite(totalRamMb)
            || !(window > 0) || !Double.isFinite(window))
            throw new IllegalArgumentException("Capacity and accounting window must be finite and positive");
        this.totalPes = totalPes;
        this.totalRamMb = totalRamMb;
        this.window = window;
    }

    void start(TaskMetadata task, double now) {
        advance(now);
        if (active.putIfAbsent(task.id(), new Reservation(task, now)) != null)
            throw new IllegalStateException("Duplicate reservation for task " + task.id());
    }

    void finish(long taskId, double now) {
        advance(now);
        var reservation = active.remove(taskId);
        if (reservation == null) throw new IllegalStateException("Missing reservation for task " + taskId);
        recent.addLast(new Completed(reservation, now));
        add(finished, reservation, now - reservation.start());
    }

    public Map<WorkloadClass, Usage> rollingShares(double now) {
        advance(now);
        double duration = Math.min(now, window);
        var usage = emptyUsage();
        for (var interval : recent) {
            var reservation = interval.reservation();
            add(usage, reservation, Math.min(duration, now - reservation.start()) - (now - interval.end()));
        }
        addActive(usage, duration, now);
        return normalize(usage, duration);
    }

    public Map<WorkloadClass, Usage> runShares(double now) {
        advance(now);
        var usage = new EnumMap<>(finished);
        addActive(usage, now, now);
        return normalize(usage, now);
    }

    private void advance(double now) {
        if (!Double.isFinite(now) || now < lastEvent) throw new IllegalArgumentException("Invalid reservation clock");
        lastEvent = now;
        while (!recent.isEmpty() && now - recent.getFirst().end() >= window) recent.removeFirst();
    }

    private void addActive(Map<WorkloadClass, Usage> usage, double duration, double now) {
        for (var reservation : active.values())
            add(usage, reservation, Math.min(duration, now - reservation.start()));
    }

    private static void add(Map<WorkloadClass, Usage> usage, Reservation reservation, double duration) {
        var task = reservation.task();
        usage.merge(task.workloadClass(), new Usage(duration * task.pes(), duration * task.ramDemandMb()), Usage::plus);
    }

    private Map<WorkloadClass, Usage> normalize(Map<WorkloadClass, Usage> usage, double duration) {
        if (duration == 0) return Map.copyOf(emptyUsage());
        usage.replaceAll((type, time) -> new Usage(time.cpu() / totalPes / duration, time.ram() / totalRamMb / duration));
        return Map.copyOf(usage);
    }

    private static EnumMap<WorkloadClass, Usage> emptyUsage() {
        var usage = new EnumMap<WorkloadClass, Usage>(WorkloadClass.class);
        for (var type : WorkloadClass.values()) usage.put(type, new Usage(0, 0));
        return usage;
    }

    private record Reservation(TaskMetadata task, double start) {}
    private record Completed(Reservation reservation, double end) {}

    /** CPU and RAM fractions of total VM capacity-time over the requested interval. */
    public record Usage(double cpu, double ram) {
        Usage plus(Usage other) { return new Usage(cpu + other.cpu, ram + other.ram); }
        public double dominant() { return Math.max(cpu, ram); }
    }
}
