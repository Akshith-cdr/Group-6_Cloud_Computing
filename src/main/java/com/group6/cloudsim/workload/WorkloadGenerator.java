package com.group6.cloudsim.workload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/** Pure seeded generation, independent of CloudSim and all scheduling policies. */
public final class WorkloadGenerator {
    public WorkloadTrace generate(WorkloadGenerationConfig config) {
        var seedSource = new Random(config.seed());
        var arrivals = new Random(seedSource.nextLong());
        var classes = new Random(seedSource.nextLong());
        var resources = new Random(seedSource.nextLong());
        var labels = new ArrayList<WorkloadClass>(config.totalTasks());
        var counts = config.scenario().classCounts(config.totalTasks());
        for (var type : WorkloadClass.values()) {
            for (int i = 0; i < counts.get(type); i++) labels.add(type);
        }
        Collections.shuffle(labels, classes);
        var tasks = new ArrayList<TaskMetadata>(config.totalTasks());
        double arrival = 0;
        for (int i = 0; i < labels.size(); i++) {
            arrival += -StrictMath.log1p(-arrivals.nextDouble()) / config.arrivalRatePerSecond();
            if (!Double.isFinite(arrival) || arrival > config.arrivalHorizonSeconds()) {
                throw new IllegalArgumentException("Arrival horizon exceeded at task " + i + " (seed " + config.seed()
                    + "). Increase horizon or rate, or reduce task count; no tasks were dropped or rescaled.");
            }
            var type = labels.get(i);
            var profile = config.profiles().get(type);
            tasks.add(new TaskMetadata(i, type, arrival,
                resources.nextLong(profile.minLengthMiPerPe(), profile.maxLengthMiPerPe() + 1),
                resources.nextInt(profile.minPes(), profile.maxPes() + 1),
                resources.nextLong(profile.minRamMb(), profile.maxRamMb() + 1),
                profile.responseTimeTargetSeconds(), config.seed()));
        }
        return new WorkloadTrace(config, tasks);
    }
}
