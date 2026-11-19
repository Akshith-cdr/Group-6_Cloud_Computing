package com.group6.cloudsim.workload;

import java.util.Objects;
import java.util.OptionalDouble;

/** Scheduler-independent inputs; arrival is seconds after workload epoch, length is MI per PE. */
public record TaskMetadata(
    long id, WorkloadClass workloadClass, double arrivalTimeSeconds,
    long lengthMiPerPe, int pes, long ramDemandMb,
    OptionalDouble responseTimeTargetSeconds, long generationSeed
) {
    public TaskMetadata {
        Objects.requireNonNull(workloadClass, "workloadClass");
        Objects.requireNonNull(responseTimeTargetSeconds, "responseTimeTargetSeconds");
        if (id < 0) throw new IllegalArgumentException("Task ID must be non-negative");
        if (!Double.isFinite(arrivalTimeSeconds) || arrivalTimeSeconds < 0)
            throw new IllegalArgumentException("Task arrival must be finite and non-negative");
        if (lengthMiPerPe <= 0 || pes <= 0 || ramDemandMb <= 0)
            throw new IllegalArgumentException("Task length, PEs and RAM demand must be positive");
        WorkloadProfile.validateTarget(responseTimeTargetSeconds);
    }
}
