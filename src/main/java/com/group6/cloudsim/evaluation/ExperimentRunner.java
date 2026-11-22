package com.group6.cloudsim.evaluation;

import com.group6.cloudsim.core.*;
import com.group6.cloudsim.dispatch.DispatchPolicy;
import com.group6.cloudsim.dispatch.FcfsPolicy;
import com.group6.cloudsim.dispatch.StrictPriorityPolicy;
import com.group6.cloudsim.dispatch.WeightedDrfPolicy;
import com.group6.cloudsim.dispatch.WmrfLgConfig;
import com.group6.cloudsim.dispatch.WmrfLgPolicy;
import com.group6.cloudsim.workload.*;
import java.util.*;
import java.util.function.Supplier;

/** Runs fresh simulation state, with policy execution separate from feasibility/measurement. */
public final class ExperimentRunner {
    public static final String STOCK_POLICY = "cloudsim-stock-space-shared";
    public enum Purpose { ORIGINAL_STRESS, UNAPPROVED_COMPARISON_CANDIDATE }
    public enum Ablation {
        WMRF_LG_FULL(true, true),
        WMRF_LG_NO_LATENCY_GUARD(false, true),
        WMRF_LG_NO_AGEING(true, false),
        WMRF_LG_NO_LATENCY_NO_AGEING(false, false);

        private final boolean latency;
        private final boolean ageing;

        Ablation(boolean latency, boolean ageing) {
            this.latency = latency;
            this.ageing = ageing;
        }

        WmrfLgConfig settings(WmrfLgConfig base) {
            return new WmrfLgConfig(base.batchWeight(), base.interactiveWeight(), base.inferenceWeight(),
                base.windowSeconds(), latency ? base.latencyCoefficient() : 0,
                ageing ? base.ageingCoefficient() : 0, base.ageingThresholdSeconds());
        }
    }
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
        SimulationController.ObservedRun run, MetricsCollector.Summary summary, Optional<WmrfLgConfig> referenceConfig) {
        public Map<WorkloadClass, MetricsCollector.ClassMetrics> metrics() { return summary.byClass(); }

        public Optional<WmrfLgConfig> wmrfConfig() {
            return WmrfLgPolicy.ID.equals(metadata.policyName())
                || Arrays.stream(Ablation.values()).anyMatch(variant -> variant.name().equals(metadata.policyName()))
                ? referenceConfig : Optional.empty();
        }

        public OptionalDouble fairness() {
            return referenceConfig.isEmpty() ? OptionalDouble.empty() : FairnessCalculator.jain(
                run.reservationShares(), run.observations(), referenceConfig.get()::weight, run.observationEnd());
        }
    }

    /** Supplier must return a new executor; executors must create fresh simulator/Cloudlets on each call. */
    public Result run(Metadata metadata, Supplier<RunExecutor> executorFactory) {
        return run(metadata, executorFactory, Optional.empty());
    }

    private Result run(Metadata metadata, Supplier<RunExecutor> executorFactory, Optional<WmrfLgConfig> referenceConfig) {
        var feasibility = new SlaFeasibilityAnalyzer().analyze(metadata.trace(), metadata.simulationConfig());
        var observed = executorFactory.get().execute(metadata.simulationConfig());
        var metrics = new MetricsCollector().summarize(feasibility, observed.observations(), observed.workloadEpoch(), observed.observationEnd());
        return new Result(metadata, feasibility, observed, metrics, referenceConfig);
    }

    public Result runStock(Metadata metadata) {
        if (!STOCK_POLICY.equals(metadata.policyName()))
            throw new IllegalArgumentException("Stock execution requires policy label " + STOCK_POLICY);
        return run(metadata, () -> new SimulationController()::runObserved);
    }

    public Result runWmrfLg(Metadata metadata, WmrfLgConfig config) {
        return runDispatched(metadata, WmrfLgPolicy.ID, new WmrfLgPolicy(config), config);
    }

    public Result runAblation(Metadata metadata, WmrfLgConfig base, Ablation variant) {
        var config = variant.settings(base);
        return runDispatched(metadata, variant.name(), new WmrfLgPolicy(config), config);
    }

    public Result runFcfs(Metadata metadata, WmrfLgConfig referenceConfig) {
        return runDispatched(metadata, FcfsPolicy.ID, new FcfsPolicy(), referenceConfig);
    }

    public Result runStrictPriority(Metadata metadata, WmrfLgConfig referenceConfig) {
        return runDispatched(metadata, StrictPriorityPolicy.ID, new StrictPriorityPolicy(), referenceConfig);
    }

    public Result runWeightedDrf(Metadata metadata, WmrfLgConfig referenceConfig) {
        return runDispatched(metadata, WeightedDrfPolicy.ID, new WeightedDrfPolicy(referenceConfig), referenceConfig);
    }

    private Result runDispatched(Metadata metadata, String policyId, DispatchPolicy policy, WmrfLgConfig referenceConfig) {
        if (!policyId.equals(metadata.policyName()))
            throw new IllegalArgumentException("Dispatch execution requires policy label " + policyId);
        return run(metadata, () -> simulation -> new SimulationController().runObserved(
            simulation, policy, referenceConfig.windowSeconds()), Optional.of(referenceConfig));
    }
}
