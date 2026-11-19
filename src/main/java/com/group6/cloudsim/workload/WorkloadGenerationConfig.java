package com.group6.cloudsim.workload;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/** Immutable generation input. Rate is tasks/second; horizon is seconds. */
public record WorkloadGenerationConfig(
    WorkloadScenario scenario, int totalTasks, long seed,
    double arrivalHorizonSeconds, double arrivalRatePerSecond,
    Map<WorkloadClass, WorkloadProfile> profiles
) {
    public WorkloadGenerationConfig {
        Objects.requireNonNull(scenario, "scenario");
        if (totalTasks < 0) throw new IllegalArgumentException("Total task count must be non-negative");
        if (!Double.isFinite(arrivalHorizonSeconds) || arrivalHorizonSeconds <= 0)
            throw new IllegalArgumentException("Arrival horizon must be finite and positive");
        if (!Double.isFinite(arrivalRatePerSecond) || arrivalRatePerSecond <= 0)
            throw new IllegalArgumentException("Arrival rate must be finite and positive");
        profiles = Map.copyOf(profiles);
        for (var type : WorkloadClass.values()) {
            var profile = profiles.get(type);
            if (profile == null) throw new IllegalArgumentException("Missing workload profile: " + type);
            if (profile.responseTimeTargetSeconds().isPresent() == (type == WorkloadClass.BATCH))
                throw new IllegalArgumentException("Batch must have no response target; interactive/inference must have one: " + type);
        }
    }

    public static WorkloadGenerationConfig defaults(WorkloadScenario scenario) {
        return new WorkloadGenerationConfig(scenario, 120, 42, 600, 0.5, defaultProfiles());
    }

    public static Map<WorkloadClass, WorkloadProfile> defaultProfiles() {
        return Map.of(
            WorkloadClass.BATCH, new WorkloadProfile(30_000, 120_000, 1, 2, 1_024, 2_048, OptionalDouble.empty()),
            WorkloadClass.INTERACTIVE, new WorkloadProfile(2_000, 10_000, 1, 1, 256, 1_024, OptionalDouble.of(5)),
            WorkloadClass.AI_INFERENCE, new WorkloadProfile(10_000, 50_000, 1, 2, 1_536, 3_072, OptionalDouble.of(10))
        );
    }
}
