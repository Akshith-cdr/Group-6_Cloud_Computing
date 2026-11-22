package com.group6.cloudsim.evaluation;

import com.group6.cloudsim.dispatch.ResourceTimeAccountant.Usage;
import com.group6.cloudsim.workload.WorkloadClass;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

/** Writes one aggregate row and one row per class for each run. */
final class CsvExporter {
    private CsvExporter() {}

    static void writeHeader(Writer writer) throws IOException {
        writeHeader(writer, false);
    }

    static void writeHeader(Writer writer, boolean traceIdentity) throws IOException {
        writer.write("policy,scenario,configuration,purpose,seed,task_count,arrival_horizon_s,arrival_rate_per_s,"
            + "workload_profiles,host_count,host,vm_count,vm,batch_weight,interactive_weight,inference_weight,"
            + "window_s,latency_coefficient,ageing_coefficient,ageing_threshold_s,measurement_start_s,measurement_end_s,"
            + "class,completed,failed,cancelled,unfinished,mean_response_s,median_response_s,p95_response_s,p99_response_s,"
            + "mean_queue_wait_s,throughput_per_s,batch_makespan_s,cpu_reserved_fraction,ram_reserved_fraction,"
            + "sla_tasks,intrinsically_feasible,intrinsically_infeasible,sla_misses,feasible_sla_misses,unresolved_sla,"
            + "sla_miss_ratio,feasible_sla_miss_ratio,jain_entitlement_normalized,max_queue_wait_s");
        if (traceIdentity) writer.write(",trace_sha256,run_id");
        writer.write('\n');
    }

    static void writeRun(Writer writer, ExperimentRunner.Result row) throws IOException {
        writeRun(writer, row, List.of());
    }

    static void writeRun(Writer writer, ExperimentRunner.Result row, List<String> traceIdentity) throws IOException {
        var metadata = row.metadata();
        var workload = metadata.trace().config();
        var sim = metadata.simulationConfig();
        var reference = row.referenceConfig().orElseThrow();
        var wmrf = row.wmrfConfig();
        String profiles = Arrays.stream(WorkloadClass.values())
            .map(type -> type + "=" + workload.profiles().get(type)).collect(Collectors.joining(" | "));
        var common = List.of(metadata.policyName(), workload.scenario(), metadata.configurationName(), metadata.purpose(),
            workload.seed(), workload.totalTasks(), workload.arrivalHorizonSeconds(), workload.arrivalRatePerSecond(),
            profiles, sim.hostCount(), sim.host(), sim.vmCount(), sim.vm(), reference.batchWeight(), reference.interactiveWeight(),
            reference.inferenceWeight(), reference.windowSeconds(),
            wmrf.map(config -> Double.toString(config.latencyCoefficient())).orElse(""),
            wmrf.map(config -> Double.toString(config.ageingCoefficient())).orElse(""),
            wmrf.map(config -> Double.toString(config.ageingThresholdSeconds())).orElse(""),
            row.run().workloadEpoch(), row.run().observationEnd());
        var usage = row.run().reservationShares();
        double cpu = 0;
        double ram = 0;
        for (var type : WorkloadClass.values()) {
            cpu += usage.get(type).cpu();
            ram += usage.get(type).ram();
        }
        boolean measured = row.run().observationEnd() > row.run().workloadEpoch();
        writeRow(writer, common, "ALL", row.summary().aggregate(), row.summary().batchMakespan(),
            measured ? new Usage(cpu, ram) : null, row.fairness(), traceIdentity);
        for (var type : WorkloadClass.values())
            writeRow(writer, common, type.name(), row.metrics().get(type),
                type == WorkloadClass.BATCH ? row.summary().batchMakespan() : OptionalDouble.empty(),
                measured ? usage.get(type) : null, OptionalDouble.empty(), traceIdentity);
    }

    private static void writeRow(Writer writer, List<?> common, String type, MetricsCollector.ClassMetrics metrics,
                                 OptionalDouble makespan, Usage usage, OptionalDouble fairness, List<String> traceIdentity) throws IOException {
        var values = new ArrayList<Object>(common);
        values.addAll(List.of(type, metrics.completed(), metrics.failed(), metrics.cancelled(), metrics.unfinished(),
            metrics.averageResponse(), metrics.medianResponse(), metrics.p95Response(), metrics.p99Response(),
            metrics.averageQueueWait(), metrics.throughput(), makespan,
            usage == null ? "" : usage.cpu(), usage == null ? "" : usage.ram(),
            metrics.slaTasks(), metrics.intrinsicallyFeasible(), metrics.intrinsicallyInfeasible(), metrics.totalSlaMisses(),
            metrics.missesAmongFeasible(), metrics.unresolvedSlaTasks(), metrics.allSlaMissRatio(),
            metrics.feasibleSlaMissRatio(), fairness, metrics.maximumQueueWait()));
        values.addAll(traceIdentity);
        writeCsvRow(writer, values);
    }

    static void writeCsvRow(Writer writer, List<?> values) throws IOException {
        writer.write(values.stream().map(CsvExporter::encode).collect(Collectors.joining(",")));
        writer.write('\n');
    }

    private static String encode(Object value) {
        String text = value instanceof OptionalDouble number
            ? (number.isPresent() ? Double.toString(number.getAsDouble()) : "") : value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r"))
            return "\"" + text.replace("\"", "\"\"") + "\"";
        return text;
    }
}
