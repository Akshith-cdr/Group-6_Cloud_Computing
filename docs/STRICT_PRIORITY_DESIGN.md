# Strict Priority design

Strict Priority provides a fixed-class baseline for measuring the latency and queueing effects
of always preferring higher-priority workloads when resources permit. It is nonpreemptive and
intentionally uses no ageing or fairness compensation.

## Dispatch order and placement

| Rank | Workload class |
| --- | --- |
| 1 | Interactive |
| 2 | AI inference |
| 3 | Batch |

Within each class, FCFS ordering uses the simulated workload arrival timestamp. The lower task
ID wins when arrival timestamps are equal. A Cloudlet's datacenter receipt and execution
timestamps are observed after selection. The dispatcher exposes only arrived tasks, and the
policy also checks each candidate against the current workload clock.

This is **work-conserving Strict Priority with feasible-task bypass**. One feasible task in a
higher class takes priority over every lower-class task. A task that cannot fit on any VM
stays in the ready queue. The policy can then choose the next eligible task in that class,
or a lower-class task if every higher-class candidate is blocked. The selected placement
is the lowest-ID VM with enough free PEs and RAM together.

`DispatchBroker` commits both resources through `VmReservations` before binding and submitting
a Cloudlet. The broker refreshes its snapshot after each dispatch and retries queued tasks
at arrival and release events. Completion, failure and cancellation use the shared release
path. Strict Priority keeps no mutable state and does not consult reservation history.

## Complexity and limits

One decision scans Q ready tasks and up to V VM snapshots per task, using O(Q × V) time
and O(1) extra space. Existing reservations are nonpreemptive, and lowest-ID placement
can fragment capacity. Under sustained higher-priority demand, Batch can be repeatedly
deferred even as resources become free. A finite trace can quantify delay and unfinished
tasks at its cutoff. Permanent starvation requires evidence beyond a finite completed run.

The same observation contract applies to every policy. On the Batch row, mean and maximum
queue wait use successful tasks with complete dispatch timestamps. The separate unfinished
count shows how many Batch tasks remain at the observation cutoff. Batch makespan spans the
first Batch arrival through the final Batch completion when the entire Batch cohort completes.
The shared Jain reference weights are used for reporting only.

## Select and reproduce

Select `STRICT_PRIORITY` in the experiment command or call
`ExperimentRunner.runStrictPriority(metadata, referenceConfig)` with that policy ID in the
metadata. The rank order is fixed. `--config FILE` can set the shared fairness reference and
ledger window. The stock path remains the default outside experiment mode.

```powershell
mvn clean test
mvn exec:java '-Dexec.args=--experiment STRICT_PRIORITY target/strict-priority-balanced.csv --scenario BALANCED --seeds 42'
mvn exec:java '-Dexec.args=--experiment STRICT_PRIORITY experiments/strict-priority-results.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
mvn exec:java '-Dexec.args=--experiment FCFS target/strict-priority-fcfs-control.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
mvn exec:java '-Dexec.args=--experiment WMRF_LG target/strict-priority-wmrf-control.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
```

The full sweep uses all four scenarios. Omit `--candidate` for original stress only. The
optional demonstration candidate is intrinsically feasible for SLA tasks and remains
unapproved. Use the same scenario, seeds, candidate and reference settings in all control
runs. See the [metric contract](EXPERIMENT_METRICS.md) and
[verification](MILESTONE_3A_VERIFICATION.md#strict-priority-baseline-verification).
