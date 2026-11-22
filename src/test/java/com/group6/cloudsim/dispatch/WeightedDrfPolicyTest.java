package com.group6.cloudsim.dispatch;

import com.group6.cloudsim.evaluation.SlaFeasibilityAnalyzer;
import com.group6.cloudsim.evaluation.SlaFeasibilityAnalyzer.TaskFeasibility;
import com.group6.cloudsim.workload.WorkloadClass;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.group6.cloudsim.dispatch.StrictPriorityPolicyTest.task;
import static com.group6.cloudsim.dispatch.ReservationSnapshot.Resources;
import static com.group6.cloudsim.workload.WorkloadClass.*;
import static org.junit.jupiter.api.Assertions.*;

class WeightedDrfPolicyTest {
    private static final WmrfLgConfig DEFAULTS = WmrfLgConfig.defaults();
    private static final Resources TOTAL = new Resources(10, 10000);
    private static final ReservationSnapshot EMPTY = new ReservationSnapshot(TOTAL, Map.of());
    private static final List<DispatchPolicy.VmCapacity> ROOM = List.of(new DispatchPolicy.VmCapacity(7, 2, 4096));
    private final WeightedDrfPolicy policy = new WeightedDrfPolicy(DEFAULTS);

    @Test void cpuRamAndEqualSharesUseTheLargerNormalizedFraction() {
        var allocations = new ReservationSnapshot(TOTAL, Map.of(BATCH, new Resources(4, 500),
            INTERACTIVE, new Resources(1, 4000), AI_INFERENCE, new Resources(2, 2000)));
        assertEquals(.4, allocations.dominantShare(BATCH));
        assertEquals(.4, allocations.dominantShare(INTERACTIVE));
        assertEquals(.2, allocations.dominantShare(AI_INFERENCE));
        assertEquals(0, EMPTY.dominantShare(BATCH));
    }

    @Test void invalidCapacityAndWeightsFailAndValidExtremeWeightsStayFinite() {
        assertThrows(IllegalArgumentException.class, () -> new ReservationSnapshot(new Resources(0, 1000), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ReservationSnapshot(new Resources(1, 0), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ReservationSnapshot(TOTAL, Map.of(BATCH, new Resources(11, 0))));
        assertThrows(IllegalStateException.class, () -> policy.select(0, List.of(), ROOM, null));
        assertEquals(.30, DEFAULTS.weight(BATCH));
        assertEquals(.35, DEFAULTS.weight(INTERACTIVE));
        assertEquals(.35, DEFAULTS.weight(AI_INFERENCE));
        for (double invalid : new double[] {0, -.1, Double.NaN, Double.POSITIVE_INFINITY, .4, Double.MIN_VALUE})
            assertThrows(IllegalArgumentException.class, () -> settings(invalid, .35, .35));
        var full = new ReservationSnapshot(TOTAL, Map.of(BATCH, TOTAL));
        assertTrue(Double.isFinite(full.dominantShare(BATCH) / settings(Double.MIN_NORMAL, .5, .5).weight(BATCH)));
    }

    @Test void configuredWeightsOrderEligibleClassesByWeightedShare() {
        var snapshot = new ReservationSnapshot(TOTAL, Map.of(BATCH, new Resources(2, 500), INTERACTIVE, new Resources(1, 2000)));
        var ready = List.of(row(0, BATCH, 0, 1, 500), row(1, INTERACTIVE, 1, 1, 500));
        assertEquals(1, select(ready, ROOM, snapshot).taskId());
        var custom = new WeightedDrfPolicy(settings(.50, .25, .25));
        assertEquals(0, custom.selectWithAllocations(2, ready, ROOM, snapshot).orElseThrow().taskId());
    }

    @Test void exactClassTiesUseIdentifiersAndIgnoreClassUrgencyAndAgeing() {
        var batch = row(0, BATCH, 0, 1, 500);
        var interactive = row(1, INTERACTIVE, 1, 1, 500);
        var inference = row(2, AI_INFERENCE, 2, 1, 500);
        var ready = List.of(interactive, batch, inference);
        assertEquals(2, select(ready, ROOM, EMPTY).taskId());
        assertEquals(0, select(List.of(interactive, batch), ROOM, EMPTY).taskId());
        assertEquals(select(ready, ROOM, EMPTY),
            policy.selectWithAllocations(1000, List.of(inference, batch, interactive), ROOM, EMPTY).orElseThrow());
        var batchService = new ReservationSnapshot(TOTAL, Map.of(BATCH, new Resources(1, 1000)));
        assertEquals(1, select(List.of(interactive, batch), ROOM, batchService).taskId());
    }

    @ParameterizedTest
    @EnumSource(WorkloadClass.class)
    void selectsByWorkloadArrivalAndThenIdWithinEveryClass(WorkloadClass type) {
        var later = row(0, type, 2, 1, 500);
        var tiedHigh = row(9, type, 0, 1, 500);
        var tiedLow = row(3, type, 0, 1, 500);
        var expected = new DispatchPolicy.Decision(3, 7);
        assertEquals(expected, select(List.of(later, tiedHigh, tiedLow), ROOM, EMPTY));
        assertEquals(expected, select(List.of(tiedLow, later, tiedHigh), ROOM, EMPTY));
        assertTrue(policy.selectWithAllocations(1, List.of(later), ROOM, EMPTY).isEmpty());
    }

    @Test void skipsBlockedClassesAndTasksAndChoosesLowestFeasibleVm() {
        var ai = row(0, AI_INFERENCE, 0, 2, 3500);
        var oldBatch = row(1, BATCH, 0, 3, 500);
        var laterBatch = row(2, BATCH, 1, 2, 2500);
        var ready = List.of(ai, oldBatch, laterBatch, row(3, INTERACTIVE, 2, 1, 500));
        var vms = List.of(new DispatchPolicy.VmCapacity(9, 2, 3000), new DispatchPolicy.VmCapacity(1, 1, 4096),
            new DispatchPolicy.VmCapacity(2, 2, 2000), new DispatchPolicy.VmCapacity(4, 2, 2500));
        assertEquals(new DispatchPolicy.Decision(2, 4), select(ready, vms, EMPTY));
        assertEquals(new DispatchPolicy.Decision(0, 7), select(ready, ROOM, EMPTY));
        assertTrue(policy.selectWithAllocations(2, ready.subList(0, 2), vms, EMPTY).isEmpty());
    }

    private DispatchPolicy.Decision select(List<TaskFeasibility> ready, List<DispatchPolicy.VmCapacity> vms,
                                            ReservationSnapshot allocations) {
        return policy.selectWithAllocations(2, ready, vms, allocations).orElseThrow();
    }

    private static WmrfLgConfig settings(double batch, double interactive, double inference) {
        return new WmrfLgConfig(batch, interactive, inference, 60, .20, .05, 60);
    }

    private static TaskFeasibility row(long id, WorkloadClass type, double arrival, int pes, long ram) {
        var vm = new com.group6.cloudsim.core.SimulationConfig.MachineConfig(4, 1000, 4096, 1000, 20000);
        return new SlaFeasibilityAnalyzer().analyze(List.of(task(id, type, arrival, pes, ram, 1000)), List.of(vm))
            .tasks().get(0);
    }
}
