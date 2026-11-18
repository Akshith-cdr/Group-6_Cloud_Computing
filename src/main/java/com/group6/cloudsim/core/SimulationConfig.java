package com.group6.cloudsim.core;

import com.group6.cloudsim.workload.TaskMetadata;
import com.group6.cloudsim.workload.WorkloadClass;
import java.util.List;

/** Resource capacities and fixed workload inputs for a simulation run. */
public record SimulationConfig(MachineConfig host, MachineConfig vm, List<TaskMetadata> tasks) {
    public SimulationConfig {
        tasks = List.copyOf(tasks);
    }

    public static SimulationConfig milestoneOne() {
        return new SimulationConfig(
            new MachineConfig(8, 2_000, 16_384, 10_000, 1_000_000),
            new MachineConfig(4, 2_000, 4_096, 1_000, 20_000),
            List.of(
                new TaskMetadata(0, WorkloadClass.BATCH, 40_000, 1, 0.15),
                new TaskMetadata(1, WorkloadClass.INTERACTIVE, 2_000, 1, 0.10),
                new TaskMetadata(2, WorkloadClass.AI_INFERENCE, 12_000, 2, 0.55)
            )
        );
    }

    /** CPU capacity is specified per PE. Memory and storage use MB, bandwidth uses Mbps. */
    public record MachineConfig(int pes, long mipsPerPe, long ramMb, long bandwidthMbps, long storageMb) {}
}
