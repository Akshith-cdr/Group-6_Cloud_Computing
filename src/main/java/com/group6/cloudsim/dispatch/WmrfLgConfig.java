package com.group6.cloudsim.dispatch;

import com.group6.cloudsim.workload.WorkloadClass;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Immutable parameters for one WMRF-LG experiment. */
public record WmrfLgConfig(double batchWeight, double interactiveWeight, double inferenceWeight,
                           double windowSeconds, double latencyCoefficient, double ageingCoefficient,
                           double ageingThresholdSeconds) {
    public WmrfLgConfig {
        positive("batchWeight", batchWeight);
        positive("interactiveWeight", interactiveWeight);
        positive("inferenceWeight", inferenceWeight);
        if (!Double.isFinite(1 / Math.min(batchWeight, Math.min(interactiveWeight, inferenceWeight))))
            throw new IllegalArgumentException("Entitlement weights must have finite reciprocals");
        if (Math.abs(batchWeight + interactiveWeight + inferenceWeight - 1) > 1e-9)
            throw new IllegalArgumentException("Entitlement weights must sum to one within 1e-9");
        positive("windowSeconds", windowSeconds);
        positive("ageingThresholdSeconds", ageingThresholdSeconds);
        nonnegative("latencyCoefficient", latencyCoefficient);
        nonnegative("ageingCoefficient", ageingCoefficient);
        if (!Double.isFinite(latencyCoefficient + ageingCoefficient))
            throw new IllegalArgumentException("Combined coefficients must be finite");
    }

    public static WmrfLgConfig defaults() {
        return new WmrfLgConfig(.30, .35, .35, 60, .20, .05, 60);
    }

    public double weight(WorkloadClass type) {
        return switch (type) {
            case BATCH -> batchWeight;
            case INTERACTIVE -> interactiveWeight;
            case AI_INFERENCE -> inferenceWeight;
        };
    }

    public static WmrfLgConfig load(Path path) throws IOException {
        var names = new String[] {"batchWeight", "interactiveWeight", "inferenceWeight", "windowSeconds",
            "latencyCoefficient", "ageingCoefficient", "ageingThresholdSeconds"};
        var defaults = defaults();
        double[] values = {defaults.batchWeight(), defaults.interactiveWeight(), defaults.inferenceWeight(),
            defaults.windowSeconds(), defaults.latencyCoefficient(), defaults.ageingCoefficient(),
            defaults.ageingThresholdSeconds()};
        var props = new Properties();
        try (var reader = Files.newBufferedReader(path)) { props.load(reader); }
        for (int i = 0; i < names.length; i++) {
            var value = props.remove(names[i]);
            if (value != null) values[i] = Double.parseDouble(value.toString());
        }
        if (!props.isEmpty()) throw new IllegalArgumentException("Unknown WMRF-LG settings " + props.keySet());
        return new WmrfLgConfig(values[0], values[1], values[2], values[3], values[4], values[5], values[6]);
    }

    private static void positive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0)
            throw new IllegalArgumentException(name + " must be finite and positive");
    }

    private static void nonnegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0)
            throw new IllegalArgumentException(name + " must be finite and nonnegative");
    }
}
