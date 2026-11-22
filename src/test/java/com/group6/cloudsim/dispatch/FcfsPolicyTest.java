package com.group6.cloudsim.dispatch;

import com.group6.cloudsim.evaluation.SlaFeasibilityAnalyzer.Classification;
import com.group6.cloudsim.evaluation.SlaFeasibilityAnalyzer.TaskFeasibility;
import com.group6.cloudsim.workload.TaskMetadata;
import com.group6.cloudsim.workload.WorkloadClass;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

import static com.group6.cloudsim.workload.WorkloadClass.*;
import static org.junit.jupiter.api.Assertions.*;

class FcfsPolicyTest {
    private final FcfsPolicy policy = new FcfsPolicy();
    private final List<DispatchPolicy.VmCapacity> available = List.of(new DispatchPolicy.VmCapacity(3, 2, 4096));

    @Test void earlierArrivalWinsRegardlessOfClassSlaOrTaskId() {
        var batch = task(9, BATCH, 1, 1, 500, 0);
        var interactive = task(2, INTERACTIVE, 2, 1, 500, 1);
        assertEquals(new DispatchPolicy.Decision(9, 3), select(List.of(interactive, batch), available));
        var laterBatch = task(0, BATCH, 3, 1, 500, 0);
        var inference = task(8, AI_INFERENCE, 2, 1, 500, 10);
        assertEquals(new DispatchPolicy.Decision(8, 3), select(List.of(laterBatch, inference), available));
    }

    @Test void equalArrivalsUseAscendingIdAndRepeatedDecisionsAreDeterministic() {
        var low = task(1, INTERACTIVE, 1, 1, 500, 10);
        var high = task(9, INTERACTIVE, 1, 1, 500, 1);
        var expected = new DispatchPolicy.Decision(1, 3);
        assertEquals(expected, select(List.of(high, low), available));
        assertEquals(expected, select(List.of(high, low), available));
        assertEquals(expected, select(List.of(low, high), available));
    }

    @Test void ignoresFutureTasksUntilTheirArrival() {
        var future = task(1, BATCH, 3, 1, 500, 0);
        assertTrue(policy.select(Math.nextDown(3.0), List.of(future), available, null).isEmpty());
        assertEquals(new DispatchPolicy.Decision(1, 3),
            policy.select(3, List.of(future), available, null).orElseThrow());
    }

    @Test void rejectsCpuAndRamUnfitTasksAndSelectsLowestFeasibleVmId() {
        var vms = List.of(new DispatchPolicy.VmCapacity(7, 2, 4096),
            new DispatchPolicy.VmCapacity(1, 1, 4096), new DispatchPolicy.VmCapacity(4, 2, 4096),
            new DispatchPolicy.VmCapacity(2, 2, 2400));
        var fits = task(5, BATCH, 2, 2, 4096, 0);
        var cpuBlocked = task(1, BATCH, 0, 3, 500, 0);
        var ramBlocked = task(2, BATCH, 1, 1, 4097, 0);
        assertTrue(policy.select(2, List.of(cpuBlocked), vms, null).isEmpty());
        assertTrue(policy.select(2, List.of(ramBlocked), vms, null).isEmpty());
        var ready = new java.util.ArrayList<>(List.of(cpuBlocked, ramBlocked, fits));
        assertEquals(new DispatchPolicy.Decision(5, 4), select(ready, vms));
        assertEquals(List.of(cpuBlocked, ramBlocked, fits), ready);
        var released = List.of(new DispatchPolicy.VmCapacity(7, 3, 4096));
        assertEquals(new DispatchPolicy.Decision(1, 7), select(ready, released));
    }

    private DispatchPolicy.Decision select(List<TaskFeasibility> ready, List<DispatchPolicy.VmCapacity> vms) {
        return policy.select(3, ready, vms, null).orElseThrow();
    }

    private static TaskFeasibility task(long id, WorkloadClass type, double arrival, int pes, long ram, double sla) {
        var target = sla == 0 ? OptionalDouble.empty() : OptionalDouble.of(sla);
        var task = new TaskMetadata(id, type, arrival, 1000, pes, ram, target, 42);
        var classification = target.isEmpty() ? Classification.NO_SLA : Classification.INTRINSICALLY_FEASIBLE;
        return new TaskFeasibility(task, 1, classification);
    }
}
