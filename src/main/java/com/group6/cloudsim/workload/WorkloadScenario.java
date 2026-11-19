package com.group6.cloudsim.workload;

import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;

/** The experimental mixes; enum declaration order breaks equal rounding remainders. */
public enum WorkloadScenario {
    BALANCED(40, 35, 25),
    BATCH_HEAVY(60, 20, 20),
    INTERACTIVE_HEAVY(20, 60, 20),
    INFERENCE_HEAVY(20, 20, 60);

    private final Map<WorkloadClass, Integer> percentages;

    WorkloadScenario(int batch, int interactive, int inference) {
        percentages = Map.of(WorkloadClass.BATCH, batch, WorkloadClass.INTERACTIVE, interactive,
            WorkloadClass.AI_INFERENCE, inference);
    }

    public Map<WorkloadClass, Integer> percentages() { return percentages; }

    /** Hamilton/largest-remainder allocation, using integer arithmetic to preserve every task. */
    public Map<WorkloadClass, Integer> classCounts(int totalTasks) {
        if (totalTasks < 0) throw new IllegalArgumentException("Total task count must be non-negative");
        var counts = new EnumMap<WorkloadClass, Integer>(WorkloadClass.class);
        for (var type : WorkloadClass.values()) {
            counts.put(type, (int) ((long) totalTasks * percentages.get(type) / 100));
        }
        int remaining = totalTasks - counts.values().stream().mapToInt(Integer::intValue).sum();
        var order = Arrays.stream(WorkloadClass.values()).sorted(
            Comparator.<WorkloadClass>comparingLong(type -> (long) totalTasks * percentages.get(type) % 100)
                .reversed().thenComparingInt(Enum::ordinal)).toList();
        for (int i = 0; i < remaining; i++) counts.merge(order.get(i), 1, Integer::sum);
        return Map.copyOf(counts);
    }
}
