package com.group6.cloudsim.evaluation;

import com.group6.cloudsim.core.*;
import com.group6.cloudsim.workload.*;
import java.util.*;
import java.util.function.Supplier;

/** Runs fresh simulation state, with policy execution separate from feasibility/measurement. */
public final class ExperimentRunner {
    public static final String STOCK_POLICY = "cloudsim-stock-space-shared";
    public enum Purpose { ORIGINAL_STRESS, UNAPPROVED_COMPARISON_CANDIDATE }
    @FunctionalInterface public interface RunExecutor {
        SimulationController.ObservedRun execute(SimulationConfig config);
    }
    /** Retains complete immutable inputs, including seed, arrivals, profiles and topology. */
    public record Metadata(String configurationName, Purpose purpose, String policyName,
                           WorkloadTrace trace, SimulationConfig simulationConfig) {
        public Metadata {
            if (configurationName == null || configurationName.isBlank() || policyName == null || policyName.isBlank())
                throw new IllegalArgumentException("Explicit configuration and policy names required");
            Objects.requireNonNull(purpose); Objects.requireNonNull(trace); Objects.requireNonNull(simulationConfig);
            if (!trace.tasks().equals(simulationConfig.tasks())) throw new IllegalArgumentException("Trace/config mismatch");
            if ((purpose == Purpose.ORIGINAL_STRESS) != configurationName.equals("original-stress"))
                throw new IllegalArgumentException("Stress and candidate configuration names must be distinct");
            if (purpose == Purpose.ORIGINAL_STRESS && !trace.config().profiles().equals(WorkloadGenerationConfig.defaultProfiles()))
                throw new IllegalArgumentException("Original stress profiles must stay unchanged");
        }
    }
    public record Result(Metadata metadata, SlaFeasibilityAnalyzer.Report feasibility,
        SimulationController.ObservedRun run, Map<WorkloadClass, MetricsCollector.ClassMetrics> metrics) {
        public Result { metrics = Map.copyOf(metrics); }
    }

    /** Supplier must return a new executor; executors must create fresh simulator/Cloudlets on each call. */
    public Result run(Metadata metadata, Supplier<RunExecutor> executorFactory) {
        var feasibility = new SlaFeasibilityAnalyzer().analyze(metadata.trace(), metadata.simulationConfig());
        var observed = executorFactory.get().execute(metadata.simulationConfig());
        var metrics = new MetricsCollector().collect(feasibility, observed.observations(), observed.workloadEpoch(), observed.observationEnd());
        return new Result(metadata, feasibility, observed, metrics);
    }

    public Result runStock(Metadata metadata) {
        if (!STOCK_POLICY.equals(metadata.policyName()))
            throw new IllegalArgumentException("Stock execution requires policy label " + STOCK_POLICY);
        return run(metadata, () -> new SimulationController()::runObserved);
    }
}
