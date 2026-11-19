package com.group6.cloudsim.workload;

import java.util.Objects;
import java.util.OptionalDouble;

/** Inclusive resource ranges. RAM is MB per task, never per PE or a percentage. */
public record WorkloadProfile(
    long minLengthMiPerPe, long maxLengthMiPerPe,
    int minPes, int maxPes, long minRamMb, long maxRamMb,
    OptionalDouble responseTimeTargetSeconds
) {
    public WorkloadProfile {
        if (minLengthMiPerPe <= 0 || maxLengthMiPerPe < minLengthMiPerPe || maxLengthMiPerPe == Long.MAX_VALUE)
            throw new IllegalArgumentException("Length range must be positive, ordered and below Long.MAX_VALUE");
        if (minPes <= 0 || maxPes < minPes || maxPes == Integer.MAX_VALUE)
            throw new IllegalArgumentException("PE range must be positive, ordered and below Integer.MAX_VALUE");
        if (minRamMb <= 0 || maxRamMb < minRamMb || maxRamMb == Long.MAX_VALUE)
            throw new IllegalArgumentException("RAM range must be positive, ordered and below Long.MAX_VALUE");
        validateTarget(Objects.requireNonNull(responseTimeTargetSeconds, "responseTimeTargetSeconds"));
    }

    static void validateTarget(OptionalDouble target) {
        if (target.isPresent() && (!Double.isFinite(target.getAsDouble()) || target.getAsDouble() <= 0))
            throw new IllegalArgumentException("Response-time target must be finite and positive when present");
    }

    public boolean contains(TaskMetadata task) {
        return task.lengthMiPerPe() >= minLengthMiPerPe && task.lengthMiPerPe() <= maxLengthMiPerPe
            && task.pes() >= minPes && task.pes() <= maxPes
            && task.ramDemandMb() >= minRamMb && task.ramDemandMb() <= maxRamMb
            && responseTimeTargetSeconds.equals(task.responseTimeTargetSeconds());
    }
}
