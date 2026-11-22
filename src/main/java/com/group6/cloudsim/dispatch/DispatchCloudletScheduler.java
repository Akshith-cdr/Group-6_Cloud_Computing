package com.group6.cloudsim.dispatch;

import java.util.function.Consumer;
import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.schedulers.cloudlet.CloudletSchedulerSpaceShared;
import org.cloudsimplus.vms.VmSimple;

/** Adds terminal notifications and PE cleanup for dispatched Cloudlets stopped before success. */
final class DispatchCloudletScheduler extends CloudletSchedulerSpaceShared {
    private final Consumer<Cloudlet> onStopped;

    DispatchCloudletScheduler(Consumer<Cloudlet> onStopped) { this.onStopped = onStopped; }

    @Override
    public Cloudlet cloudletFail(Cloudlet cloudlet) {
        return stopRunning(cloudlet, Cloudlet.Status.FAILED);
    }

    @Override
    public Cloudlet cloudletCancel(Cloudlet cloudlet) {
        return stopRunning(cloudlet, Cloudlet.Status.CANCELED);
    }

    private Cloudlet stopRunning(Cloudlet cloudlet, Cloudlet.Status status) {
        var running = findCloudletInList(cloudlet, getCloudletExecList());
        if (running.isEmpty()) return Cloudlet.NULL;
        // The stock 8.5.7 stop path removes the list entry without updating used PEs.
        var execution = removeCloudletFromExecList(running.get());
        execution.setStatus(status);
        ((VmSimple) getVm()).addExpectedFreePesNumber(cloudlet.getPesNumber());
        onStopped.accept(cloudlet);
        return cloudlet;
    }
}
