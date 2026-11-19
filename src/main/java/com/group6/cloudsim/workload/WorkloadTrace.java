package com.group6.cloudsim.workload;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable validated input, reusable to construct fresh tasks for every policy. */
public record WorkloadTrace(WorkloadGenerationConfig config, List<TaskMetadata> tasks) {
    public WorkloadTrace {
        Objects.requireNonNull(config, "config");
        tasks = List.copyOf(tasks);
        WorkloadValidator.validate(config, tasks);
    }

    public Map<WorkloadClass, Integer> classCounts() {
        return config.scenario().classCounts(tasks.size());
    }
}
