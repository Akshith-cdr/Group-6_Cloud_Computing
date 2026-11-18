package com.group6.cloudsim.workload;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.cloudlets.CloudletSimple;
import org.cloudsimplus.utilizationmodels.UtilizationModelDynamic;

/** Converts task inputs into fresh Cloudlets for each run. */
public final class CloudletFactory {
    private CloudletFactory() {}

    public static Cloudlet create(TaskMetadata task) {
        Cloudlet cloudlet = new CloudletSimple(task.id(), task.lengthMiPerPe(), task.pes());
        // CloudletSimple requests full CPU on each PE by default.
        cloudlet.setUtilizationModelRam(new UtilizationModelDynamic(task.ramFraction()));
        return cloudlet;
    }
}
