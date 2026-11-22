package com.group6.cloudsim.workload;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;

/** Immutable validated input, reusable to construct fresh tasks for every policy. */
public record WorkloadTrace(WorkloadGenerationConfig config, List<TaskMetadata> tasks) {
    public WorkloadTrace {
        Objects.requireNonNull(config, "config");
        tasks = List.copyOf(tasks);
        WorkloadValidator.validate(config, tasks);
    }

    public Map<WorkloadClass, Integer> classCounts() {
        return config.scenario().classCounts(tasks.size());
    }

    /** Versioned fingerprint of the validated configuration and ordered task payload. */
    public String fingerprint() {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var out = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                out.writeUTF("group6-workload-trace-v1");
                out.writeUTF(config.scenario().name());
                out.writeInt(config.totalTasks());
                out.writeLong(config.seed());
                out.writeDouble(config.arrivalHorizonSeconds());
                out.writeDouble(config.arrivalRatePerSecond());
                var classes = WorkloadClass.values();
                out.writeInt(classes.length);
                for (var type : classes) {
                    var profile = config.profiles().get(type);
                    out.writeUTF(type.name());
                    out.writeLong(profile.minLengthMiPerPe());
                    out.writeLong(profile.maxLengthMiPerPe());
                    out.writeInt(profile.minPes());
                    out.writeInt(profile.maxPes());
                    out.writeLong(profile.minRamMb());
                    out.writeLong(profile.maxRamMb());
                    writeTarget(out, profile.responseTimeTargetSeconds());
                }
                out.writeInt(tasks.size());
                for (var task : tasks) {
                    out.writeLong(task.id());
                    out.writeUTF(task.workloadClass().name());
                    out.writeDouble(task.arrivalTimeSeconds());
                    out.writeLong(task.lengthMiPerPe());
                    out.writeInt(task.pes());
                    out.writeLong(task.ramDemandMb());
                    writeTarget(out, task.responseTimeTargetSeconds());
                    out.writeLong(task.generationSeed());
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException("Could not fingerprint workload trace", e);
        }
    }

    private static void writeTarget(DataOutputStream out, OptionalDouble target) throws IOException {
        out.writeBoolean(target.isPresent());
        if (target.isPresent()) out.writeDouble(target.getAsDouble());
    }
}
