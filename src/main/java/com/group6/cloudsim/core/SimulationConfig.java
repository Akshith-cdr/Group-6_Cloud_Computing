package com.group6.cloudsim.core;

import com.group6.cloudsim.workload.TaskMetadata;
import com.group6.cloudsim.workload.WorkloadClass;
import com.group6.cloudsim.workload.WorkloadTrace;
import com.group6.cloudsim.workload.WorkloadValidator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/** Resource capacities and fixed workload inputs for a simulation run. */
public record SimulationConfig(MachineConfig host, int hostCount, MachineConfig vm, int vmCount, List<TaskMetadata> tasks) {
    public SimulationConfig {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(vm, "vm");
        if (hostCount <= 0 || vmCount <= 0) throw new IllegalArgumentException("Host and VM counts must be positive");
        if (vm.mipsPerPe() > host.mipsPerPe()) throw new IllegalArgumentException("VM MIPS per PE exceeds host capacity");
        long slotsPerHost = Math.min(Math.min(host.pes() / vm.pes(), host.ramMb() / vm.ramMb()),
            Math.min(host.bandwidthMbps() / vm.bandwidthMbps(), host.storageMb() / vm.storageMb()));
        if ((long) hostCount * slotsPerHost < vmCount)
            throw new IllegalArgumentException("VM topology does not fit host CPU, RAM, bandwidth or storage without overcommit");
        tasks = List.copyOf(tasks);
        WorkloadValidator.validateTasks(tasks);
        WorkloadValidator.validateForVm(tasks, vm.pes(), vm.ramMb());
    }

    public SimulationConfig(MachineConfig host, MachineConfig vm, List<TaskMetadata> tasks) {
        this(host, 1, vm, 1, tasks);
    }

    public static SimulationConfig research(WorkloadTrace trace) {
        return new SimulationConfig(
            new MachineConfig(4, 1_000, 16_384, 10_000, 1_000_000), 3,
            new MachineConfig(2, 1_000, 4_096, 1_000, 20_000), 6, trace.tasks());
    }

    public static SimulationConfig milestoneOne() {
        return new SimulationConfig(
            new MachineConfig(8, 2_000, 16_384, 10_000, 1_000_000),
            new MachineConfig(4, 2_000, 4_096, 1_000, 20_000),
            List.of(
                new TaskMetadata(0, WorkloadClass.BATCH, 0, 40_000, 1, 614, OptionalDouble.empty(), 42),
                new TaskMetadata(1, WorkloadClass.INTERACTIVE, 0, 2_000, 1, 410, OptionalDouble.of(5), 42),
                new TaskMetadata(2, WorkloadClass.AI_INFERENCE, 0, 12_000, 2, 2_253, OptionalDouble.of(10), 42)
            )
        );
    }

    /** CPU capacity is specified per PE. Memory and storage use MB, bandwidth uses Mbps. */
    public record MachineConfig(int pes, long mipsPerPe, long ramMb, long bandwidthMbps, long storageMb) {
        public MachineConfig {
            if (pes <= 0 || mipsPerPe <= 0 || ramMb <= 0 || bandwidthMbps <= 0 || storageMb <= 0)
                throw new IllegalArgumentException("All machine capacities must be positive");
        }
    }
}
