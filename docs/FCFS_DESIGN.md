# FCFS design

`FCFS` is work-conserving first-come, first-served with feasible-task bypass. It gives priority
to the lowest workload arrival timestamp among arrived tasks that can currently fit on a VM.
The lower task ID wins when arrivals are equal. Workload class, SLA target, ageing, entitlement
and resource history play no role in the ordering.

## Dispatch and placement

`FcfsPolicy` implements `DispatchPolicy`. The broker constructs the ready set on the simulated
workload clock measured from VM readiness. The policy scans that set and considers only tasks
whose timestamps are at or before the current workload time. Each task needs both its requested
PEs and its required RAM on the same VM. The placement is the feasible VM with the lowest ID,
independent of the order of the snapshot.

Under strict head-of-line FIFO, an older task waiting for resources would block the rest of
the queue. With feasible-task bypass, a later task can start if the older task cannot fit on
any VM. The broker keeps the older task in its ready set and reconsiders it after each arrival
or reservation release.

`DispatchBroker` and `VmReservations` are shared by FCFS and WMRF-LG. The broker commits
both resources atomically before binding and submitting a Cloudlet. If no arrived task fits,
the broker waits for the next event. Completion, failure and cancellation release the
reservation through the same scheduler and broker paths.

For Q ready tasks and V eligible VMs, one decision takes O(Q × V) time and O(1) extra space.
The broker refreshes the capacity snapshot after each admission. The lowest-ID placement
rule can fragment capacity. Under a sustained workload of smaller jobs, a large task waiting
for simultaneous resources can be bypassed repeatedly. FCFS offers no SLA or starvation
guarantee.

## Select and reproduce

Compatible Java entry points are `ExperimentRunner.runFcfs(metadata, referenceConfig)` with
metadata policy `FCFS`, and the existing experiment CLI. A single default balanced trace
uses the approved balanced inputs and the default workload seed of 42.

```powershell
mvn clean test
mvn exec:java '-Dexec.args=--experiment FCFS target/fcfs-balanced.csv --scenario BALANCED --seeds 42'
mvn exec:java '-Dexec.args=--experiment FCFS experiments/fcfs-results.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
mvn exec:java '-Dexec.args=--experiment WMRF_LG target/fcfs-wmrf-control.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
```

Omitting `--scenario` runs all four scenarios. Omitting `--candidate` runs original stress
profiles only. The named demonstration candidate preserves original task controls and is
intrinsically feasible for SLA-bearing tasks. It is unapproved.

Use the same `--config FILE` for both experiments if overriding default weights. The shared
weights normalize Jain fairness for both policies, and the shared window configures the
reservation ledger. FCFS uses neither in dispatch decisions. Its WMRF-LG-specific latency
and ageing columns are empty in the common CSV schema. The stock simulation remains the
default for commands without `--experiment`.

See the [experiment metric contract](EXPERIMENT_METRICS.md) and
[verification report](MILESTONE_3A_VERIFICATION.md#fcfs-baseline-verification).
