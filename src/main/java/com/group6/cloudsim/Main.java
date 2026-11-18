package com.group6.cloudsim;

import com.group6.cloudsim.core.SimulationConfig;
import com.group6.cloudsim.core.SimulationController;
import java.util.Locale;

/** Runs the Milestone 1 scenario and prints the completed Cloudlets. */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        var results = new SimulationController().run(SimulationConfig.milestoneOne());
        System.out.printf("%nCompleted cloudlets (%d)%n", results.size());
        System.out.printf(
            "%-8s %-14s %-8s %3s %7s %10s %10s %10s%n",
            "Cloudlet", "Workload", "Status", "VM", "PEs", "Start (s)", "Finish (s)", "Run (s)"
        );
        for (var result : results) {
            var cloudlet = result.cloudlet();
            System.out.printf(
                Locale.ROOT, "%-8d %-14s %-8s %3d %7d %10.2f %10.2f %10.2f%n",
                cloudlet.getId(), result.metadata().workloadClass(), cloudlet.getStatus(),
                cloudlet.getVm().getId(), cloudlet.getPesNumber(),
                cloudlet.getStartTime(), cloudlet.getFinishTime(), cloudlet.getTotalExecutionTime()
            );
        }
    }
}
