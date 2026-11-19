package com.group6.cloudsim.evaluation;

import com.group6.cloudsim.workload.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Explicit, named candidates override only lengths and targets; other controls stay fixed. */
public final class Calibration {
    private Calibration() {}
    public record Candidate(String name, WorkloadGenerationConfig config) {
        public Candidate {
            if (name == null || name.isBlank() || name.equals("original-stress"))
                throw new IllegalArgumentException("Candidate needs a distinct nonempty name");
            Objects.requireNonNull(config);
        }
    }

    public static Candidate load(Path path, WorkloadGenerationConfig original) throws IOException {
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(path)) { properties.load(reader); }
        var allowed = new HashSet<>(Set.of("name"));
        var profiles = new EnumMap<>(original.profiles());
        for (var type : WorkloadClass.values()) {
            String prefix = type.name() + ".";
            allowed.add(prefix + "minLengthMiPerPe"); allowed.add(prefix + "maxLengthMiPerPe");
            if (type != WorkloadClass.BATCH) allowed.add(prefix + "slaSeconds");
            var p = profiles.get(type);
            var target = type == WorkloadClass.BATCH ? OptionalDouble.empty()
                : OptionalDouble.of(Double.parseDouble(properties.getProperty(prefix + "slaSeconds",
                    Double.toString(p.responseTimeTargetSeconds().orElseThrow()))));
            profiles.put(type, new WorkloadProfile(
                Long.parseLong(properties.getProperty(prefix + "minLengthMiPerPe", Long.toString(p.minLengthMiPerPe()))),
                Long.parseLong(properties.getProperty(prefix + "maxLengthMiPerPe", Long.toString(p.maxLengthMiPerPe()))),
                p.minPes(), p.maxPes(), p.minRamMb(), p.maxRamMb(), target));
        }
        for (var key : properties.stringPropertyNames())
            if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown calibration key: " + key);
        return new Candidate(properties.getProperty("name"), new WorkloadGenerationConfig(original.scenario(), original.totalTasks(),
            original.seed(), original.arrivalHorizonSeconds(), original.arrivalRatePerSecond(), profiles));
    }
}
