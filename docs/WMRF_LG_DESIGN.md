# WMRF-LG design

Workload-Aware Weighted Multi-Resource Fairness with Latency Guard is a weighted, DRF-inspired
heuristic for non-preemptive, mixed batch, interactive and AI-inference workloads. Batch work
needs progress over time. Interactive requests benefit from earlier admission when an SLA is still
plausible. Inference can consume a larger share of RAM than CPU. WMRF-LG ranks ready work using both
resources and gives bounded preference to latency and waiting time. It has no formal DRF guarantees.

## Dispatch architecture

The merged Milestone 3A checkout had the stock broker only, so this milestone introduces the
arrival-driven interface needed by the algorithm. `DispatchBroker` extends
`DatacenterBrokerSimple`, preserves physical VM placement, and holds future tasks separately from
the ready set. Its arrival events use the workload epoch established when all VMs are ready. At a
decision event the broker inserts every task due on that clock. The policy receives only ready tasks,
snapshots of per-VM free PEs and RAM, and access to historical reservation shares.

`VmReservations` reserves both resources together on the simulation thread, and the broker binds
the admitted Cloudlet to the chosen VM before submitting it with zero additional delay.
`DispatchCloudletScheduler` retains CloudSim's space-shared execution. Successful Cloudlet returns
release the reservation. Explicit fail/cancel events for running tasks use a scheduler stop path
that returns the PEs to CloudSim and notifies the broker. Each release triggers reconsideration of
queued work. Duplicate terminal notifications cannot release a reservation twice. Same-clock
notifications are coalesced when already queued for a decision, and remaining event ordering follows
CloudSim's deterministic event order. There are no periodic policy polling or wall-clock timers.
The smoke and default research paths still use the stock broker.

## Resource accounting

For time `t` measured from VM readiness, let `L = min(t, W)` and use the interval
`[t - L, t)`. For each admitted task, intersect the interval with its reservation from dispatch
through release. A task still running contributes through the current time. Let `x_j` be the
duration of intersection for task `j`.

```text
C(c,t) = sum(j in c, x_j * requested_PEs_j) / (total VM PEs * L)
R(c,t) = sum(j in c, x_j * reserved_MB_j) / (total VM RAM_MB * L)
D(c,t) = max(C(c,t), R(c,t)) / entitlement(c)
```

Shares at `L = 0` are zero. Equal-MIPS VMs make PE-time proportional to CPU capacity-time in the
current topology. Idle time remains in the denominator. Completed intervals expire from the rolling
history as the window moves forward. Separate per-class totals let the same ledger report
full-run occupancy. Simultaneous changes add zero duration at their boundary and do not double
count. Reservations include any time between dispatch and actual execution or release. Reported
CPU/RAM figures measure reserved VM capacity-time, including any such delay.

## Latency guard, ageing and score

Let `wait = max(0, t - workloadArrival)`. The SLA analyzer supplies `idealService` as the fastest
eligible VM's CPU-only bound, `MI per PE / MIPS per PE`. For the current homogeneous topology this
bound is the same for all eligible placements. For an intrinsically feasible SLA-bearing task,

```text
U(j,t) = min(1, (wait + idealService) / SLATarget)
A(j,t) = min(1, wait / T_age)
Score(j,t) = D(class(j),t) - latencyCoefficient * U(j,t) - ageingCoefficient * A(j,t)
```

Urgency reaches one at zero remaining ideal slack and stays bounded after that point. Batch,
tasks without an SLA, and intrinsically infeasible tasks have urgency zero. Ageing increases with
simulated waiting time for every class and saturates at `T_age`. It raises priority for older work
and reduces starvation risk. It supplies no starvation-freedom bound under sustained arrival or
persistent resource fragmentation.

Each iteration chooses the admissible task with the lowest score. Exact Java `double` comparisons
(no epsilon) order scores in this sequence, followed by earlier trace arrival, smaller task ID and
smaller eligible VM ID. The broker rebuilds the capacity snapshot after each admission. Elapsed
resource-time at the same decision timestamp is zero, even if several tasks are admitted there.
Class weights express entitlement. Any class can borrow idle capacity when a task fits a VM;
rolling history retains that service when other classes subsequently contend. A placement requires
both resources on one VM. This tie-breaking can leave fragmented capacity.

## Configuration

| Properties key | Default |
| --- | ---: |
| `batchWeight` | 0.30 |
| `interactiveWeight` | 0.35 |
| `inferenceWeight` | 0.35 |
| `windowSeconds` | 60 |
| `latencyCoefficient` | 0.20 |
| `ageingCoefficient` | 0.05 |
| `ageingThresholdSeconds` | 60 |

Pass a properties file to `--config` to override any of these keys. Weights must be positive,
have finite reciprocals and sum to one within an absolute tolerance of `1e-9`. Window and age
threshold must be finite and strictly positive. Coefficients and their sum must be finite, and
each coefficient must be nonnegative. Unknown properties are rejected. The Java entry point uses
the immutable `WmrfLgConfig` record.

## Complexity and scope

For `Q` ready tasks, `V` VMs, `R` active reservations and `H` completed reservations still in the
window, one selection is O(Q*V + R + H). At a dispatch event the scheduler repeats selection
after each successful admission, and may perform one final unsuccessful search. The service
ledger uses O(R + H) space with fixed-size per-class cumulative totals. The broker also keeps the
trace, Cloudlets and observation timestamps in O(N) space and the VM capacity ledger in O(V) space.

The latency estimate excludes simulator granularity, queueing and other resource delay. Exact
CPU-bound equality can therefore still produce a measured SLA miss. The experiment models
homogeneous, fixed-size VMs and non-preemptive CPU tasks with RAM admission. VM/host failures,
migration, paused or retried Cloudlets and device-level utilization are outside the validated model.
Jain measures service/entitlement across arrived classes; class demand varies, so a lower value can
also occur when one class cannot use its entitlement. See the [metric contract](EXPERIMENT_METRICS.md).

## Run and reproduce

```powershell
mvn clean test
mvn exec:java '-Dexec.args=--experiment WMRF_LG experiments/wmrf-lg-results.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
mvn exec:java '-Dexec.args=--feasibility experiments/service-bound-demo.properties'
```

The experiment command runs all four approved stress scenarios. The optional candidate file adds an
explicit paired run per scenario and seed while preserving the original trace controls. The checked-in
candidate demonstrates intrinsic feasibility and remains unapproved. Omit `--candidate` for a
stress-only sweep. The CSV has one aggregate row and three class rows per run and retains the complete
generation, machine and policy configuration. FCFS, Strict Priority and Weighted DRF use the
same dispatcher and measurements. The stock simulation uses different admission rules. See the
[FCFS design](FCFS_DESIGN.md), [Strict Priority design](STRICT_PRIORITY_DESIGN.md) and [Weighted DRF design](WEIGHTED_DRF_DESIGN.md).
