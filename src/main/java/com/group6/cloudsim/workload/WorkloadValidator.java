package com.group6.cloudsim.workload;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;

/** Validates imported/generated records without depending on a scheduler or simulator. */
public final class WorkloadValidator {
    private WorkloadValidator() {}

    public static void validateTasks(List<TaskMetadata> tasks) {
        var ids = new HashSet<Long>();
        double previous = 0;
        for (var task : tasks) {
            if (!ids.add(task.id())) throw new IllegalArgumentException("Duplicate task ID: " + task.id());
            if (task.arrivalTimeSeconds() < previous)
                throw new IllegalArgumentException("Task arrivals must be ordered; task " + task.id());
            previous = task.arrivalTimeSeconds();
        }
    }

    public static void validate(WorkloadGenerationConfig config, List<TaskMetadata> tasks) {
        validateTasks(tasks);
        if (tasks.size() != config.totalTasks()) throw new IllegalArgumentException("Trace task count differs from configuration");
        var counts = new EnumMap<WorkloadClass, Integer>(WorkloadClass.class);
        for (var type : WorkloadClass.values()) counts.put(type, 0);
        for (var task : tasks) {
            if (task.generationSeed() != config.seed()) throw new IllegalArgumentException("Task seed differs: " + task.id());
            if (task.arrivalTimeSeconds() > config.arrivalHorizonSeconds())
                throw new IllegalArgumentException("Task exceeds arrival horizon: " + task.id());
            if (!config.profiles().get(task.workloadClass()).contains(task))
                throw new IllegalArgumentException("Task outside configured resource/response target profile: " + task.id());
            counts.merge(task.workloadClass(), 1, Integer::sum);
        }
        if (!counts.equals(config.scenario().classCounts(config.totalTasks())))
            throw new IllegalArgumentException("Trace class distribution differs from scenario " + config.scenario());
    }

    /** Per-task feasibility only: concurrent RAM reservations require a later admission ledger. */
    public static void validateForVm(List<TaskMetadata> tasks, int vmPes, long vmRamMb) {
        for (var task : tasks) {
            if (task.pes() > vmPes || task.ramDemandMb() > vmRamMb)
                throw new IllegalArgumentException("Task " + task.id() + " exceeds VM PE or RAM capacity");
        }
    }
}
