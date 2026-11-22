# Weighted DRF-inspired baseline

The `WEIGHTED_DRF` policy provides a multi-resource comparison for WMRF-LG. Dominant Resource
Fairness extends max-min fairness to multiple resources by comparing users' dominant shares.
This project adapts that idea to workload classes and discrete, nonpreemptive Cloudlets on
individual VMs. [Ghodsi et al., NSDI 2011](https://www.usenix.org/conference/nsdi11/dominant-resource-fairness-fair-allocation-multiple-resource-types)

## Current reservation shares

For workload class `c`, the policy uses these quantities at each dispatch decision.

```text
CPUShare_c = ReservedPEs_c / TotalSchedulablePEs
RAMShare_c = ReservedRAM_c / TotalSchedulableRAM
D_c        = max(CPUShare_c, RAMShare_c)
WD_c       = D_c / Weight_c
```

`VmReservations` sums the PE and RAM capacities of the created, schedulable VMs. It also sums
each class's active reservations across those VMs. A task contributes from atomic reservation
at dispatch through release after completion, failure, or cancellation. Waiting tasks contribute
zero. The snapshot validates positive capacity and checks that aggregate reservations fit within
it. An unavailable VM with a live reservation is a simulation error.

| Class | Default entitlement |
| --- | ---: |
| Batch | 0.30 |
| Interactive | 0.35 |
| AI inference | 0.35 |

The default entitlements and validation come from the same configuration as WMRF-LG. Set
`batchWeight`, `interactiveWeight`, and `inferenceWeight` in a properties file passed with
`--config FILE`. Weights must be finite and positive, have finite reciprocals, and sum to
one within an absolute tolerance of `1e-9`. Use the same values for each policy in a comparison
so the Jain fairness normalization is shared.

## Selection and admission

This is **Weighted DRF-inspired scheduling with work-conserving feasible-task bypass**.
Among classes with an arrived task that fits on one VM, the lowest current weighted
dominant share wins. An exact share tie is resolved by ascending class identifier
(`AI_INFERENCE`, `BATCH`, `INTERACTIVE`). Within the chosen class, earlier workload
arrival wins, followed by lower task ID. Blocked tasks remain queued, so later feasible
tasks within a class or in another class can use the available capacity. Placement picks
the lowest VM ID with enough PEs and RAM together.

The broker computes a fresh read-only allocation snapshot inside its dispatch loop.
Each policy decision following an admission sees the reservation made by the preceding
decision, including at the same simulation timestamp. Only the broker commits reservations,
binds VMs and submits Cloudlets. The existing completion, failure, and cancellation path
releases resources and reconsiders queued work.

WMRF-LG continues to rank tasks using rolling PE-time and RAM-time service, latency
urgency, and ageing. Weighted DRF uses instantaneous reservations and FCFS within a
class. Its scheduling score uses only the entitlement-normalized share.

## Complexity and limitations

For V VMs, A active reservations, C classes, and Q ready tasks, snapshot construction costs
O(V + A + C) time and O(C) extra space. Selection scans the ready set and up to V placements
per task, costing O(C + Q × V) time and O(C) extra space. The broker repeats both after each
admission. CPU is measured in PEs, consistent with the supplied homogeneous topology. RAM is
measured in MB.

Class weights are scheduling entitlements. Lowest-ID placement can fragment capacity, and
large tasks can wait for simultaneous capacity. Exact class ties favor the fixed identifier
order. These choices and discrete, nonpreemptive admissions make this a heuristic with no
formal DRF guarantees for arbitrary Cloudlet workloads.

## Select and reproduce

Use the CLI policy ID `WEIGHTED_DRF` or call `ExperimentRunner.runWeightedDrf(metadata, referenceConfig)`
with that ID in the metadata. The stock path remains the default outside experiment mode.

```powershell
mvn clean test
mvn exec:java '-Dexec.args=--experiment WEIGHTED_DRF target/weighted-drf-balanced.csv --scenario BALANCED --seeds 42'
mvn exec:java '-Dexec.args=--experiment WEIGHTED_DRF experiments/weighted-drf-results.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
mvn exec:java '-Dexec.args=--experiment FCFS target/weighted-drf-fcfs-control.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
mvn exec:java '-Dexec.args=--experiment STRICT_PRIORITY target/weighted-drf-strict-control.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
mvn exec:java '-Dexec.args=--experiment WMRF_LG target/weighted-drf-wmrf-control.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
```

Without `--scenario`, the experiment sweeps all four scenarios. Omitting `--candidate` selects
original stress only. The named demonstration candidate remains unapproved. The reproduction
commands put control exports under `target` and preserve the historical result files.
See the [metric contract](EXPERIMENT_METRICS.md) and
[verification](MILESTONE_3A_VERIFICATION.md#weighted-drf-baseline-verification).
