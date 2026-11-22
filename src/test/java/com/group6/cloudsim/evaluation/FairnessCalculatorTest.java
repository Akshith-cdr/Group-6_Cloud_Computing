package com.group6.cloudsim.evaluation;

import com.group6.cloudsim.dispatch.ResourceTimeAccountant.Usage;
import com.group6.cloudsim.dispatch.WmrfLgConfig;
import com.group6.cloudsim.workload.TaskMetadata;
import com.group6.cloudsim.workload.WorkloadClass;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

import static com.group6.cloudsim.workload.WorkloadClass.*;
import static org.junit.jupiter.api.Assertions.*;

class FairnessCalculatorTest {
    @Test void comparesDominantUsageRelativeToEntitlement() {
        var shares = Map.of(BATCH, new Usage(.3, 0), INTERACTIVE, new Usage(0, .35), AI_INFERENCE, new Usage(.35, .1));
        assertEquals(1, FairnessCalculator.jain(shares, List.of(task(0, BATCH, 0), task(1, INTERACTIVE, 0),
            task(2, AI_INFERENCE, 0)), WmrfLgConfig.defaults()::weight, 1).orElseThrow(), 1e-10);
    }

    @Test void includesArrivedZeroServiceClassAndExcludesFutureOrAbsentClasses() {
        var shares = Map.of(BATCH, new Usage(.3, 0), INTERACTIVE, new Usage(0, 0));
        assertEquals(.5, FairnessCalculator.jain(shares, List.of(task(0, BATCH, 0), task(1, INTERACTIVE, 0),
            task(2, AI_INFERENCE, 10)), WmrfLgConfig.defaults()::weight, 1).orElseThrow(), 1e-10);
    }

    @Test void handlesEmptyZeroServiceAndInvalidDenominator() {
        var tasks = List.of(task(0, BATCH, 0));
        assertTrue(FairnessCalculator.jain(Map.of(), List.of(), type -> 1, 1).isEmpty());
        assertTrue(FairnessCalculator.jain(Map.of(BATCH, new Usage(0, 0)), tasks, type -> 1, 1).isEmpty());
        assertTrue(FairnessCalculator.jain(Map.of(BATCH, new Usage(1, 0)), tasks, type -> 0, 1).isEmpty());
    }

    private static TaskObservation task(long id, WorkloadClass type, double arrival) {
        var empty = OptionalDouble.empty();
        var metadata = new TaskMetadata(id, type, arrival, 1000, 1, 500, empty, 42);
        return new TaskObservation(metadata, TaskObservation.Outcome.UNFINISHED, arrival, empty, empty, empty, empty, empty);
    }
}
