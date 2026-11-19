package com.group6.cloudsim.workload;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class WorkloadValidatorTest {
    private final WorkloadGenerationConfig config = WorkloadGenerationConfig.defaults(WorkloadScenario.BALANCED);

    private static TaskMetadata task(long id, WorkloadClass type, double arrival, long length, int pes, long ram,
                                     OptionalDouble target, long seed) {
        return new TaskMetadata(id, type, arrival, length, pes, ram, target, seed);
    }

    @Test
    void rejectsInvalidTaskFieldsAndProfileRanges() {
        assertThrows(IllegalArgumentException.class, () -> task(-1, WorkloadClass.BATCH, 0, 1, 1, 1, OptionalDouble.empty(), 42));
        assertThrows(IllegalArgumentException.class, () -> task(0, WorkloadClass.BATCH, 0, 0, 1, 1, OptionalDouble.empty(), 42));
        assertThrows(IllegalArgumentException.class, () -> task(0, WorkloadClass.BATCH, 0, 1, 0, 1, OptionalDouble.empty(), 42));
        assertThrows(IllegalArgumentException.class, () -> task(0, WorkloadClass.BATCH, 0, 1, 1, 0, OptionalDouble.empty(), 42));
        assertThrows(NullPointerException.class, () -> task(0, null, 0, 1, 1, 1, OptionalDouble.empty(), 42));
        assertThrows(IllegalArgumentException.class, () -> new WorkloadProfile(2, 1, 1, 2, 1, 2, OptionalDouble.empty()));
        assertThrows(IllegalArgumentException.class, () -> new WorkloadProfile(1, 2, 2, 1, 1, 2, OptionalDouble.empty()));
        assertThrows(IllegalArgumentException.class, () -> new WorkloadProfile(1, 2, 1, 2, 2, 1, OptionalDouble.empty()));
        assertThrows(IllegalArgumentException.class, () -> new WorkloadProfile(1, Long.MAX_VALUE, 1, 2, 1, 2, OptionalDouble.empty()));
    }

    @Test
    void rejectsMissingProfilesAndWrongClassTargets() {
        var profiles = new EnumMap<>(config.profiles());
        profiles.remove(WorkloadClass.BATCH);
        assertThrows(IllegalArgumentException.class, () -> new WorkloadGenerationConfig(config.scenario(), 120, 42, 600, 0.5, profiles));
        profiles.put(WorkloadClass.BATCH, new WorkloadProfile(1, 1, 1, 1, 1, 1, OptionalDouble.of(5)));
        assertThrows(IllegalArgumentException.class, () -> new WorkloadGenerationConfig(config.scenario(), 120, 42, 600, 0.5, profiles));
        profiles.put(WorkloadClass.BATCH, config.profiles().get(WorkloadClass.BATCH));
        profiles.put(WorkloadClass.INTERACTIVE, new WorkloadProfile(1, 1, 1, 1, 1, 1, OptionalDouble.empty()));
        assertThrows(IllegalArgumentException.class, () -> new WorkloadGenerationConfig(config.scenario(), 120, 42, 600, 0.5, profiles));
        assertThrows(IllegalArgumentException.class, () -> new WorkloadGenerationConfig(config.scenario(), -1, 42, 600, 0.5, config.profiles()));
    }

    @Test
    void rejectsDuplicateUnorderedAndWrongCountImportedTraces() {
        var tasks = new WorkloadGenerator().generate(config).tasks();
        var duplicate = new ArrayList<>(tasks);
        duplicate.set(1, tasks.get(0));
        assertThrows(IllegalArgumentException.class, () -> new WorkloadTrace(config, duplicate));
        var unordered = new ArrayList<>(tasks);
        java.util.Collections.swap(unordered, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> new WorkloadTrace(config, unordered));
        assertThrows(IllegalArgumentException.class, () -> new WorkloadTrace(config, tasks.subList(0, 119)));
    }

    @Test
    void rejectsIncorrectMixSeedResourcesTargetAndHorizon() {
        var original = new WorkloadGenerator().generate(config).tasks();
        var first = original.get(0);
        var tasks = new ArrayList<>(original);
        tasks.set(0, task(first.id(), first.workloadClass(), first.arrivalTimeSeconds(), first.lengthMiPerPe(), first.pes(),
            first.ramDemandMb(), first.responseTimeTargetSeconds(), 99));
        assertThrows(IllegalArgumentException.class, () -> new WorkloadTrace(config, tasks));
        tasks.set(0, task(first.id(), first.workloadClass(), first.arrivalTimeSeconds(), 1, first.pes(),
            first.ramDemandMb(), first.responseTimeTargetSeconds(), 42));
        assertThrows(IllegalArgumentException.class, () -> new WorkloadTrace(config, tasks));
        tasks.set(0, task(first.id(), first.workloadClass(), first.arrivalTimeSeconds(), first.lengthMiPerPe(), first.pes(),
            first.ramDemandMb(), first.responseTimeTargetSeconds().isPresent() ? OptionalDouble.empty() : OptionalDouble.of(5), 42));
        assertThrows(IllegalArgumentException.class, () -> new WorkloadTrace(config, tasks));
        var last = original.get(119);
        tasks.set(0, first);
        tasks.set(119, task(last.id(), last.workloadClass(), 601, last.lengthMiPerPe(), last.pes(),
            last.ramDemandMb(), last.responseTimeTargetSeconds(), 42));
        assertThrows(IllegalArgumentException.class, () -> new WorkloadTrace(config, tasks));

        // Each replacement is inside the target class's profile, so only the mix is wrong.
        var incorrectMix = new ArrayList<>(original);
        var type = first.workloadClass() == WorkloadClass.BATCH ? WorkloadClass.INTERACTIVE : WorkloadClass.BATCH;
        var profile = config.profiles().get(type);
        incorrectMix.set(0, task(first.id(), type, first.arrivalTimeSeconds(), profile.minLengthMiPerPe(), profile.minPes(),
            profile.minRamMb(), profile.responseTimeTargetSeconds(), 42));
        assertThrows(IllegalArgumentException.class, () -> new WorkloadTrace(config, incorrectMix));
    }

    @Test
    void rejectsTasksThatExceedVmLimits() {
        var task = task(0, WorkloadClass.BATCH, 0, 30_000, 3, 4_097, OptionalDouble.empty(), 42);
        assertThrows(IllegalArgumentException.class, () -> WorkloadValidator.validateForVm(List.of(task), 2, 8_192));
        assertThrows(IllegalArgumentException.class, () -> WorkloadValidator.validateForVm(List.of(task), 4, 4_096));
    }
}
