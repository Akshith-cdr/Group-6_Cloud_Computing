package com.group6.cloudsim.dispatch;

import com.group6.cloudsim.core.SimulationConfig;
import com.group6.cloudsim.evaluation.SlaFeasibilityAnalyzer;
import com.group6.cloudsim.evaluation.SlaFeasibilityAnalyzer.TaskFeasibility;
import com.group6.cloudsim.workload.TaskMetadata;
import com.group6.cloudsim.workload.WorkloadClass;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

import static com.group6.cloudsim.workload.WorkloadClass.*;
import static org.junit.jupiter.api.Assertions.*;

class WmrfLgPolicyTest {
    private static final double EPS = 1e-10;
    private final WmrfLgPolicy policy = new WmrfLgPolicy(WmrfLgConfig.defaults());
    private final List<DispatchPolicy.VmCapacity> room = List.of(new DispatchPolicy.VmCapacity(1, 4, 4000));

    @Test void distinguishesCpuAndRamDominantServiceAndKeepsFinishedHistory() {
        var service = new ResourceTimeAccountant(4, 4000, 60);
        service.start(task(0, BATCH, 0, 2, 100, 40000, 0), 0);
        service.start(task(1, AI_INFERENCE, 0, 1, 2000, 10000, 10), 0);
        var atTen = service.rollingShares(10);
        assertEquals(new ResourceTimeAccountant.Usage(.5, .025), atTen.get(BATCH));
        assertEquals(new ResourceTimeAccountant.Usage(.25, .5), atTen.get(AI_INFERENCE));
        service.finish(0, 10);
        service.finish(1, 10);
        assertEquals(atTen, service.rollingShares(10));
        assertEquals(.25, service.rollingShares(20).get(BATCH).dominant(), EPS);
        assertEquals(.25, service.rollingShares(20).get(AI_INFERENCE).dominant(), EPS);
    }

    @Test void clipsWindowAcrossCompletionsAndLongRunningTasks() {
        var service = new ResourceTimeAccountant(4, 4000, 60);
        service.start(task(0, BATCH, 0, 1, 1000, 40000, 0), 0);
        service.start(task(1, BATCH, 0, 1, 1000, 40000, 0), 0);
        service.finish(0, 20);
        assertEquals(.25 + .25 * 10 / 60, service.rollingShares(70).get(BATCH).cpu(), EPS);
        assertEquals(.25, service.rollingShares(80).get(BATCH).cpu(), EPS);
        service.finish(1, 90);
        assertEquals(.25 * 30 / 60, service.rollingShares(120).get(BATCH).cpu(), EPS);
        assertEquals(0, service.rollingShares(150).get(BATCH).cpu(), EPS);
        assertEquals(.25 * 110 / 150, service.runShares(150).get(BATCH).cpu(), EPS);
    }

    @Test void simultaneousFinishesAndStartsNeverDoubleCount() {
        var service = new ResourceTimeAccountant(4, 4000, 60);
        service.start(task(0, BATCH, 0, 2, 1000, 40000, 0), 0);
        service.start(task(1, BATCH, 0, 2, 1000, 40000, 0), 0);
        service.finish(0, 4);
        service.finish(1, 4);
        service.start(task(2, INTERACTIVE, 4, 4, 2000, 40000, 5), 4);
        var before = service.rollingShares(4);
        assertEquals(1, before.get(BATCH).cpu(), EPS);
        assertEquals(0, before.get(INTERACTIVE).cpu(), EPS);
        assertEquals(before, service.rollingShares(4));
        var after = service.rollingShares(8);
        assertEquals(.5, after.get(BATCH).cpu(), EPS);
        assertEquals(.5, after.get(INTERACTIVE).cpu(), EPS);
    }

    @Test void weightsBreakEqualHistoryAndBorrowingRemainsWorkConserving() {
        var service = new ResourceTimeAccountant(4, 4000, 60);
        var batch = task(0, BATCH, 0, 1, 1000, 40000, 0);
        var interactive = task(1, INTERACTIVE, 0, 1, 1000, 40000, 5);
        service.start(batch, 0);
        service.start(interactive, 0);
        service.finish(0, 10);
        service.finish(1, 10);
        var rows = List.of(feasibility(batch), feasibility(interactive));
        assertEquals(new DispatchPolicy.Decision(1, 1), policy.select(10, rows, room, service).orElseThrow());
        assertEquals(new DispatchPolicy.Decision(0, 1), policy.select(10, rows.subList(0, 1), room, service).orElseThrow());
    }

    @Test void urgencyUsesSlaAndIdealServiceAndClampsAtOne() {
        var feasible = feasibility(task(0, INTERACTIVE, 2, 1, 1000, 1000, 5));
        assertEquals(.2, WmrfLgPolicy.urgency(feasible, 2), EPS);
        assertEquals(.6, WmrfLgPolicy.urgency(feasible, 4), EPS);
        assertEquals(1, WmrfLgPolicy.urgency(feasible, 1000), EPS);
        var infeasible = feasibility(task(0, INTERACTIVE, 2, 1, 1000, 10000, 5));
        var batch = feasibility(task(0, BATCH, 2, 1, 1000, 10000, 0));
        assertEquals(0, WmrfLgPolicy.urgency(infeasible, 1000), EPS);
        assertEquals(0, WmrfLgPolicy.urgency(batch, 1000), EPS);
        assertEquals(1, policy.ageing(infeasible, 1000), EPS);
    }

    @Test void ageingAndLatencyTermsAffectNumericScore() {
        var batch = feasibility(task(0, BATCH, 1, 1, 1000, 40000, 0));
        assertEquals(0, policy.ageing(batch, 1), EPS);
        assertEquals(.5, policy.ageing(batch, 31), EPS);
        assertEquals(1, policy.ageing(batch, 61), EPS);
        assertEquals(1, policy.ageing(batch, 1000), EPS);
        assertEquals(.375, policy.score(batch, 31, .4), EPS);
        var interactive = feasibility(task(1, INTERACTIVE, 1, 1, 1000, 3000, 5));
        assertEquals(.28, policy.score(interactive, 1, .4), EPS);
        var service = new ResourceTimeAccountant(4, 4000, 60);
        assertEquals(1, policy.select(1, List.of(batch, interactive), room, service).orElseThrow().taskId());
    }

    @Test void filtersFutureAndUnfitWorkAndBreaksExactTiesByArrivalIdAndVm() {
        var service = new ResourceTimeAccountant(4, 4000, 60);
        var vms = List.of(new DispatchPolicy.VmCapacity(8, 2, 4000), new DispatchPolicy.VmCapacity(1, 1, 700),
            new DispatchPolicy.VmCapacity(4, 2, 4000), new DispatchPolicy.VmCapacity(2, 1, 4000));
        var rows = List.of(feasibility(task(2, BATCH, 1, 2, 1000, 40000, 0)),
            feasibility(task(3, BATCH, 0, 2, 1000, 40000, 0)),
            feasibility(task(4, BATCH, 0, 2, 1000, 40000, 0)),
            feasibility(task(1, BATCH, 0, 2, 4096, 40000, 0)),
            feasibility(task(0, BATCH, 500, 2, 1000, 40000, 0)));
        assertTrue(policy.select(0, rows.subList(3, 5), vms, service).isEmpty());
        assertEquals(new DispatchPolicy.Decision(3, 4), policy.select(100, rows, vms, service).orElseThrow());
        var reversed = new java.util.ArrayList<>(rows);
        java.util.Collections.reverse(reversed);
        assertEquals(policy.select(100, rows, vms, service), policy.select(100, reversed, vms, service));
        assertTrue(policy.select(500, rows, List.of(), service).isEmpty());
    }

    @Test void invalidConfigFailsAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new WmrfLgConfig(0, .5, .5, 60, .2, .05, 60));
        assertThrows(IllegalArgumentException.class, () -> new WmrfLgConfig(.2, .3, .4, 60, .2, .05, 60));
        assertThrows(IllegalArgumentException.class, () -> new WmrfLgConfig(.3, .35, .35, 0, .2, .05, 60));
        assertThrows(IllegalArgumentException.class, () -> new WmrfLgConfig(.3, .35, .35, 60, -.2, .05, 60));
        assertThrows(IllegalArgumentException.class, () -> new WmrfLgConfig(.3, .35, .35, 60, .2, Double.NaN, 60));
        assertThrows(IllegalArgumentException.class, () -> new WmrfLgConfig(.3, .35, .35, 60, .2, .05, Double.POSITIVE_INFINITY));
    }

    static TaskMetadata task(long id, WorkloadClass type, double arrival, int pes, long ram, long length, double sla) {
        return new TaskMetadata(id, type, arrival, length, pes, ram,
            sla == 0 ? OptionalDouble.empty() : OptionalDouble.of(sla), 42);
    }

    private static TaskFeasibility feasibility(TaskMetadata task) {
        return new SlaFeasibilityAnalyzer().analyze(List.of(task),
            List.of(new SimulationConfig.MachineConfig(4, 1000, 4096, 1000, 20000))).tasks().get(0);
    }
}
