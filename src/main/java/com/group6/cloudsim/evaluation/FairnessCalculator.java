package com.group6.cloudsim.evaluation;

import com.group6.cloudsim.dispatch.ResourceTimeAccountant.Usage;
import com.group6.cloudsim.workload.WorkloadClass;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.ToDoubleFunction;

/** Jain index over entitlement-normalized dominant reservation shares for arrived classes. */
final class FairnessCalculator {
    private FairnessCalculator() {}

    static OptionalDouble jain(Map<WorkloadClass, Usage> usage, List<TaskObservation> tasks,
                               ToDoubleFunction<WorkloadClass> entitlement, double observationEnd) {
        var classes = tasks.stream().filter(t -> t.workloadArrival() <= observationEnd)
            .map(t -> t.task().workloadClass()).distinct().toList();
        double[] service = new double[classes.size()];
        for (int i = 0; i < classes.size(); i++) {
            var type = classes.get(i);
            double weight = entitlement.applyAsDouble(type);
            var share = usage.get(type);
            if (share == null || !(weight > 0) || !Double.isFinite(weight)) return OptionalDouble.empty();
            service[i] = share.dominant() / weight;
            if (!Double.isFinite(service[i]) || service[i] < 0) return OptionalDouble.empty();
        }
        double max = java.util.Arrays.stream(service).max().orElse(0);
        if (max == 0) return OptionalDouble.empty();
        double sum = 0, squares = 0;
        for (double value : service) {
            double scaled = value / max;
            sum += scaled;
            squares += scaled * scaled;
        }
        return OptionalDouble.of(sum * sum / (service.length * squares));
    }
}
