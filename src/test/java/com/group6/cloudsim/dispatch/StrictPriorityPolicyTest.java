package com.group6.cloudsim.dispatch;

import com.group6.cloudsim.core.SimulationConfig.MachineConfig;
import com.group6.cloudsim.evaluation.SlaFeasibilityAnalyzer;
import com.group6.cloudsim.evaluation.SlaFeasibilityAnalyzer.TaskFeasibility;
import com.group6.cloudsim.workload.TaskMetadata;
import com.group6.cloudsim.workload.WorkloadClass;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static com.group6.cloudsim.workload.WorkloadClass.*;
import static org.junit.jupiter.api.Assertions.*;

class StrictPriorityPolicyTest {
    private final StrictPriorityPolicy policy = new StrictPriorityPolicy();
    private final List<DispatchPolicy.VmCapacity> room = List.of(new DispatchPolicy.VmCapacity(0, 2, 4096));

    @ParameterizedTest
    @CsvSource({"INTERACTIVE, AI_INFERENCE", "INTERACTIVE, BATCH", "AI_INFERENCE, BATCH"})
    void higherClassWinsEvenWhenLowerClassArrivedFirst(WorkloadClass higher, WorkloadClass lower) {
        var low = row(1, lower, 0, 1, 500);
        var high = row(9, higher, 2, 1, 500);
        assertEquals(new DispatchPolicy.Decision(9, 0), select(List.of(low, high), room));
    }

    @ParameterizedTest
    @EnumSource(WorkloadClass.class)
    void ordersEachClassByWorkloadArrivalThenId(WorkloadClass type) {
        var later = row(0, type, 2, 1, 500);
        var tiedHighId = row(9, type, 1, 1, 500);
        var tiedLowId = row(3, type, 1, 1, 500);
        var ready = List.of(later, tiedHighId, tiedLowId);
        var expected = new DispatchPolicy.Decision(3, 0);
        assertEquals(expected, select(ready, room));
        assertEquals(expected, select(ready, room));
        assertEquals(expected, select(List.of(tiedLowId, later, tiedHighId), room));
    }

    @Test void higherPriorityFutureTaskDoesNotBlockArrivedWork() {
        var future = row(0, INTERACTIVE, 3, 1, 500);
        var batch = row(1, BATCH, 0, 1, 500);
        assertTrue(policy.select(Math.nextDown(3.0), List.of(future), room, null).isEmpty());
        assertEquals(new DispatchPolicy.Decision(1, 0),
            policy.select(Math.nextDown(3.0), List.of(future, batch), room, null).orElseThrow());
        assertEquals(new DispatchPolicy.Decision(0, 0), select(List.of(future, batch), room));
    }

    @Test void bypassesBlockedClassesAndRestoresPriorityOnceResourcesFit() {
        var interactive = row(2, INTERACTIVE, 0, 2, 2000);
        var inference = row(1, AI_INFERENCE, 0, 1, 3000);
        var batch = row(0, BATCH, 0, 1, 500);
        var ready = new java.util.ArrayList<>(List.of(batch, inference, interactive));
        assertEquals(new DispatchPolicy.Decision(1, 4),
            select(ready, List.of(new DispatchPolicy.VmCapacity(4, 1, 3000))));
        assertEquals(new DispatchPolicy.Decision(0, 4),
            select(ready, List.of(new DispatchPolicy.VmCapacity(4, 1, 2999))));
        assertEquals(List.of(batch, inference, interactive), ready);
        assertEquals(new DispatchPolicy.Decision(2, 0), select(ready, room));
    }

    @Test void choosesLowestVmIdThatFitsBothResources() {
        var interactive = row(1, INTERACTIVE, 0, 2, 3000);
        var cpuBlocked = new DispatchPolicy.VmCapacity(0, 1, 4096);
        var ramBlocked = new DispatchPolicy.VmCapacity(1, 2, 2999);
        assertTrue(policy.select(3, List.of(interactive), List.of(cpuBlocked, ramBlocked), null).isEmpty());
        var vms = List.of(new DispatchPolicy.VmCapacity(9, 2, 4096), cpuBlocked,
            new DispatchPolicy.VmCapacity(5, 2, 3000), ramBlocked);
        assertEquals(new DispatchPolicy.Decision(1, 5), select(List.of(interactive), vms));
    }

    private DispatchPolicy.Decision select(List<TaskFeasibility> ready, List<DispatchPolicy.VmCapacity> vms) {
        return policy.select(3, ready, vms, null).orElseThrow();
    }

    private static TaskFeasibility row(long id, WorkloadClass type, double arrival, int pes, long ram) {
        return new SlaFeasibilityAnalyzer().analyze(List.of(task(id, type, arrival, pes, ram, 1000)),
            List.of(new MachineConfig(2, 1000, 4096, 1000, 20000))).tasks().get(0);
    }

    static TaskMetadata task(long id, WorkloadClass type, double arrival, int pes, long ram, long length) {
        var target = switch (type) {
            case INTERACTIVE -> OptionalDouble.of(5);
            case AI_INFERENCE -> OptionalDouble.of(10);
            case BATCH -> OptionalDouble.empty();
        };
        return new TaskMetadata(id, type, arrival, length, pes, ram, target, 42);
    }
}
