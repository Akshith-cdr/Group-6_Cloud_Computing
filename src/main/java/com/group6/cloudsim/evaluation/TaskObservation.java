package com.group6.cloudsim.evaluation;

import com.group6.cloudsim.workload.TaskMetadata;
import java.util.Objects;
import java.util.OptionalDouble;

/** All timestamps use absolute simulation seconds; absent means unobserved, never zero. */
public record TaskObservation(TaskMetadata task, Outcome outcome, double workloadArrival,
    OptionalDouble readyQueueEntry, OptionalDouble dispatch, OptionalDouble cloudSimSubmission,
    OptionalDouble executionStart, OptionalDouble completion) {
    public enum Outcome { COMPLETED, FAILED, CANCELLED, UNFINISHED }

    public TaskObservation {
        Objects.requireNonNull(task); Objects.requireNonNull(outcome);
        if (!Double.isFinite(workloadArrival) || workloadArrival < 0) throw new IllegalArgumentException("Invalid arrival");
        for (var value : new OptionalDouble[] {readyQueueEntry, dispatch, cloudSimSubmission, executionStart, completion}) {
            Objects.requireNonNull(value);
            if (value.isPresent() && (!Double.isFinite(value.getAsDouble()) || value.getAsDouble() < 0))
                throw new IllegalArgumentException("Invalid timestamp");
        }
        ordered(OptionalDouble.of(workloadArrival), readyQueueEntry);
        ordered(OptionalDouble.of(workloadArrival), dispatch);
        ordered(readyQueueEntry, dispatch);
        ordered(dispatch, executionStart);
        ordered(OptionalDouble.of(workloadArrival), executionStart);
        ordered(executionStart, completion);
        if (outcome == Outcome.COMPLETED && (executionStart.isEmpty() || completion.isEmpty()))
            throw new IllegalArgumentException("Completed task requires start and completion");
        if (outcome != Outcome.COMPLETED && completion.isPresent())
            throw new IllegalArgumentException("Only successful completion has a completion timestamp");
    }

    private static void ordered(OptionalDouble first, OptionalDouble second) {
        if (first.isPresent() && second.isPresent() && first.getAsDouble() > second.getAsDouble() + 1e-8)
            throw new IllegalArgumentException("Timestamps out of order");
    }

    private static OptionalDouble difference(OptionalDouble end, OptionalDouble start) {
        return end.isPresent() && start.isPresent() ? OptionalDouble.of(end.getAsDouble() - start.getAsDouble()) : OptionalDouble.empty();
    }

    public OptionalDouble responseTime() { return difference(completion, OptionalDouble.of(workloadArrival)); }
    public OptionalDouble queueWaitTime() { return difference(dispatch, OptionalDouble.of(workloadArrival)); }
    public OptionalDouble preExecutionDelay() { return difference(executionStart, dispatch); }
    public OptionalDouble serviceTime() { return difference(completion, executionStart); }
}
