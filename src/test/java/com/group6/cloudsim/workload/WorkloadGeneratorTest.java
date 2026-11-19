package com.group6.cloudsim.workload;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class WorkloadGeneratorTest {
    private final WorkloadGenerator generator = new WorkloadGenerator();

    @ParameterizedTest
    @EnumSource(WorkloadScenario.class)
    void sameSeedProducesExactlyIdenticalRecordsForEveryScenario(WorkloadScenario scenario) {
        var config = WorkloadGenerationConfig.defaults(scenario);
        assertEquals(generator.generate(config), new WorkloadGenerator().generate(config));
        var different = new WorkloadGenerationConfig(scenario, 120, 43, 600, 0.5, config.profiles());
        assertNotEquals(generator.generate(config).tasks(), generator.generate(different).tasks());
        // Compare resource/arrival values, so a changed seed field alone cannot satisfy the check.
        assertNotEquals(generator.generate(config).tasks().stream().map(TaskMetadata::arrivalTimeSeconds).toList(),
            generator.generate(different).tasks().stream().map(TaskMetadata::arrivalTimeSeconds).toList());
    }

    @ParameterizedTest
    @CsvSource({"BALANCED,48,42,30", "BATCH_HEAVY,72,24,24", "INTERACTIVE_HEAVY,24,72,24", "INFERENCE_HEAVY,24,24,72"})
    void exactDefaultClassDistribution(WorkloadScenario scenario, int batch, int interactive, int inference) {
        var trace = generator.generate(WorkloadGenerationConfig.defaults(scenario));
        assertEquals(List.of(batch, interactive, inference), List.of(count(trace, WorkloadClass.BATCH),
            count(trace, WorkloadClass.INTERACTIVE), count(trace, WorkloadClass.AI_INFERENCE)));
    }

    private static int count(WorkloadTrace trace, WorkloadClass type) {
        return (int) trace.tasks().stream().filter(task -> task.workloadClass() == type).count();
    }

    @ParameterizedTest
    @EnumSource(WorkloadScenario.class)
    void preservesCountsIdsOrderRangesAndTargetsAcrossSeedsAndSmallCounts(WorkloadScenario scenario) {
        for (long seed : new long[] {0, 1, 42, -10, Long.MAX_VALUE}) {
            for (int size : new int[] {0, 1, 2, 3, 7, 119, 120, 121}) {
                var config = new WorkloadGenerationConfig(scenario, size, seed, 10_000, 0.5,
                    WorkloadGenerationConfig.defaultProfiles());
                var trace = generator.generate(config);
                assertEquals(size, trace.tasks().size());
                var ids = new HashSet<Long>();
                double previous = 0;
                for (var task : trace.tasks()) {
                    assertTrue(ids.add(task.id()));
                    assertTrue(task.arrivalTimeSeconds() >= previous && task.arrivalTimeSeconds() <= 10_000);
                    previous = task.arrivalTimeSeconds();
                    assertTrue(config.profiles().get(task.workloadClass()).contains(task));
                    assertEquals(task.workloadClass() != WorkloadClass.BATCH, task.responseTimeTargetSeconds().isPresent());
                    assertEquals(seed, task.generationSeed());
                }
                for (var type : WorkloadClass.values()) assertEquals(config.scenario().classCounts(size).get(type), count(trace, type));
            }
        }
    }

    @Test
    void largestRemaindersHaveStableTiesAndDoNotOverflow() {
        assertEquals(List.of(1, 1, 0), WorkloadClassValues.counts(WorkloadScenario.BALANCED, 2));
        assertEquals(List.of(2, 1, 0), WorkloadClassValues.counts(WorkloadScenario.BATCH_HEAVY, 3));
        for (var scenario : WorkloadScenario.values()) {
            assertEquals(Integer.MAX_VALUE, scenario.classCounts(Integer.MAX_VALUE).values().stream().mapToLong(Integer::longValue).sum());
        }
    }

    private static final class WorkloadClassValues {
        static List<Integer> counts(WorkloadScenario scenario, int n) {
            var counts = scenario.classCounts(n);
            return List.of(counts.get(WorkloadClass.BATCH), counts.get(WorkloadClass.INTERACTIVE), counts.get(WorkloadClass.AI_INFERENCE));
        }
    }

    @Test
    void rateControlsActualInterArrivalTimesAndMixDoesNotChangeThem() {
        var profiles = WorkloadGenerationConfig.defaultProfiles();
        var slow = generator.generate(new WorkloadGenerationConfig(WorkloadScenario.BALANCED, 120, 42, 10_000, 0.5, profiles));
        var fast = generator.generate(new WorkloadGenerationConfig(WorkloadScenario.INFERENCE_HEAVY, 120, 42, 10_000, 1, profiles));
        for (int i = 0; i < 120; i++) assertEquals(slow.tasks().get(i).arrivalTimeSeconds() / 2,
            fast.tasks().get(i).arrivalTimeSeconds(), 1e-10);
        var labels = slow.tasks().stream().map(TaskMetadata::workloadClass).toList();
        assertNotEquals(labels.stream().sorted().toList(), labels, "Labels must be shuffled across arrival positions");
    }

    @Test
    void exponentialProcessHasExpectedMeanAndVariabilityForFixedLargeSample() {
        var trace = generator.generate(new WorkloadGenerationConfig(WorkloadScenario.BALANCED, 10_000, 123,
            100_000, 2, WorkloadGenerationConfig.defaultProfiles()));
        double previous = 0, sum = 0, squares = 0;
        for (var task : trace.tasks()) {
            double interval = task.arrivalTimeSeconds() - previous;
            previous = task.arrivalTimeSeconds();
            sum += interval;
            squares += interval * interval;
        }
        double mean = sum / 10_000;
        assertEquals(0.5, mean, 0.025);
        assertEquals(0.25, squares / 10_000 - mean * mean, 0.04);
    }

    @Test
    void obeysCustomFixedResourceRangesAndIsImmutable() {
        var profiles = new EnumMap<WorkloadClass, WorkloadProfile>(WorkloadClass.class);
        for (var type : WorkloadClass.values()) profiles.put(type,
            new WorkloadProfile(777, 777, 1, 1, 123, 123, type == WorkloadClass.BATCH ? OptionalDouble.empty() : OptionalDouble.of(9)));
        var config = new WorkloadGenerationConfig(WorkloadScenario.BALANCED, 10, 42, 600, 0.5, profiles);
        profiles.clear();
        var trace = generator.generate(config);
        assertTrue(trace.tasks().stream().allMatch(task -> task.lengthMiPerPe() == 777 && task.ramDemandMb() == 123 && task.pes() == 1));
        assertThrows(UnsupportedOperationException.class, () -> trace.tasks().clear());
        assertThrows(UnsupportedOperationException.class, () -> config.profiles().clear());
        var copy = new ArrayList<>(trace.tasks());
        var copiedTrace = new WorkloadTrace(config, copy);
        copy.clear();
        assertEquals(10, copiedTrace.tasks().size());
    }

    @Test
    void horizonOverflowFailsClearlyWithoutDroppingOrCompressingTasks() {
        var config = new WorkloadGenerationConfig(WorkloadScenario.BALANCED, 120, 42, 0.0001, 0.5,
            WorkloadGenerationConfig.defaultProfiles());
        assertTrue(assertThrows(IllegalArgumentException.class, () -> generator.generate(config)).getMessage().contains("horizon exceeded"));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsInvalidRatesHorizonsAndTargets(double invalid) {
        var profiles = WorkloadGenerationConfig.defaultProfiles();
        assertThrows(IllegalArgumentException.class, () -> new WorkloadGenerationConfig(WorkloadScenario.BALANCED, 120, 42, 600, invalid, profiles));
        assertThrows(IllegalArgumentException.class, () -> new WorkloadGenerationConfig(WorkloadScenario.BALANCED, 120, 42, invalid, 0.5, profiles));
        assertThrows(IllegalArgumentException.class, () -> new WorkloadProfile(1, 2, 1, 2, 1, 2, OptionalDouble.of(invalid)));
        assertThrows(IllegalArgumentException.class, () -> new TaskMetadata(0, WorkloadClass.BATCH, invalid == 0 ? -1 : invalid,
            1, 1, 1, OptionalDouble.empty(), 42));
    }
}
