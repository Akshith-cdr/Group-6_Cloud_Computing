package com.group6.cloudsim.evaluation;

import com.group6.cloudsim.core.SimulationConfig;
import com.group6.cloudsim.workload.TaskMetadata;
import com.group6.cloudsim.workload.WorkloadClass;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetricBoundaryTest {
    @Test void exactAbsoluteDeadlineIsMetEvenWhenResponseSubtractionRoundsUp() {
        var task = new TaskMetadata(0, WorkloadClass.INTERACTIVE, 3.05, 2000, 1, 500, at(5), 42);
        var feasibility = new SlaFeasibilityAnalyzer().analyze(List.of(task), List.of(SimulationConfig.milestoneOne().vm()));
        double arrival = task.arrivalTimeSeconds();
        double deadline = arrival + 5;
        var onTime = observation(task, arrival, deadline);
        assertTrue(onTime.responseTime().orElseThrow() > 5);
        var metrics = new MetricsCollector();
        assertEquals(0, metrics.summarize(feasibility, List.of(onTime), 0, deadline).aggregate().totalSlaMisses());
        double next = Math.nextUp(deadline);
        assertEquals(1, metrics.summarize(feasibility, List.of(observation(task, arrival, next)), 0, next)
            .aggregate().totalSlaMisses());
    }

    private static TaskObservation observation(TaskMetadata task, double arrival, double end) {
        return new TaskObservation(task, TaskObservation.Outcome.COMPLETED, arrival,
            at(arrival), at(arrival), at(arrival), at(arrival), at(end));
    }

    private static OptionalDouble at(double time) { return OptionalDouble.of(time); }
}
