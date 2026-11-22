# Experiment metric contract

## Execution paths

The stock path retains the Milestone 3A completion and feasibility metrics. WMRF-LG, FCFS,
Strict Priority and Weighted DRF use the same collector and arrival-driven broker, with observed arrivals and dispatches,
atomic CPU/RAM reservations, elapsed-time occupancy and Jain fairness.
See the [Milestone 3A audit](MILESTONE_3A_AUDIT.md).

## CPU lower bound

`SlaFeasibilityAnalyzer` produces one immutable record per task and class aggregates.
For each task, retain only VM types with enough PEs and RAM for that task. On an eligible VM:

```text
total work MI = lengthMiPerPe * requestedPes
ideal capacity MIPS = vmMipsPerPe * requestedPes
idealServiceSeconds = lengthMiPerPe / vmMipsPerPe
task lower bound = minimum idealServiceSeconds across eligible VMs
```

The calculation uses double division without multiplying longs, avoiding overflow. No eligible
VM gives positive infinity, hence an SLA-bearing task is infeasible. Research configuration
validation normally rejects such a task before execution. The current homogeneous topology has
six 2-PE VMs at 1,000 MIPS/PE; VM count does not accelerate a single Cloudlet.

This follows the exact published [CloudSim Plus 8.5.7 sources](https://repo.maven.apache.org/maven2/org/cloudsimplus/cloudsimplus/8.5.7/cloudsimplus-8.5.7-sources.jar):
`CloudletSimple`/`CloudletAbstract` retain per-PE length; `CloudletExecution.getRemainingCloudletLength`
tracks per-PE work; `CloudletSchedulerAbstract.updateCloudletProcessing` advances per-PE MI by
allocated per-PE MIPS times elapsed processing time. Requested PEs multiply both work and capacity.
Tests check eligible capacities and real one/two-PE smoke executions.

Assumptions: full CPU utilization, no queue, transfer, startup, RAM/BW oversubscription, preemption,
or scheduler delays. This is a CPU-only necessary condition, not a guarantee of meeting an SLA.
CloudSim event granularity/integer work updates can add delay (the smoke's 6-second inference
lower bound has measured service 6.11 seconds). Equality with an SLA is intrinsically feasible
even when simulator overhead or resource effects cause a measured miss.

## Timestamp contract

All `TaskObservation` timestamps use absolute simulation seconds. Task metadata retains trace
arrival relative to the workload epoch, which is measured when all configured VMs are created.

| Timestamp | Meaning and current source |
| --- | --- |
| Workload arrival | measured workload epoch + `TaskMetadata.arrivalTimeSeconds()` |
| Ready-queue entry | actual arrival-driven queue insertion; absent in stock broker |
| Dispatch | actual policy commitment/admission to a VM; absent in stock broker |
| CloudSim submission | clock at the actual `broker.submitCloudletList` call, at the epoch on stock and at dispatch for the dispatched policies |
| Execution start | `Cloudlet.getStartTime()`, absent when its value is unassigned |
| Completion | `Cloudlet.getFinishTime()` only when status is `SUCCESS` |

`getDcArrivalTime()` records datacenter receipt. Stock sends `CLOUDLET_SUBMIT` with a future
submission delay; the dispatched path binds and submits the Cloudlet only after the ledger commits
both resources. Dispatch and datacenter receipt coincide in the current zero-transfer-delay
configuration. Unknown timestamps use empty `OptionalDouble`; the CLI displays `N/A`.

```text
responseTime = completion - workloadArrival
queueWaitTime = dispatch - workloadArrival
preExecutionDelay = executionStart - dispatch
serviceTime = completion - executionStart
responseTime = queueWaitTime + preExecutionDelay + serviceTime
```

Queue wait includes any delay before ready insertion. Service is elapsed time since execution
start, including stalls; it is not pure CPU busy time. Validation rejects invalid ordering,
nonfinite timestamps, and successful completion without start/finish.
The collector also checks workload arrival against epoch + trace arrival and requires all observed
events to fall inside the observation window. Ready entry and CloudSim submission cannot follow
execution start, including when dispatch is unobserved.

## Outcomes, latency, and throughput

`SimulationController.runObserved` retains an observation for every input Cloudlet. `SUCCESS`
means completed; `FAILED`/`FAILED_RESOURCE_UNAVAILABLE` mean failed; `CANCELED` means cancelled;
all other states mean unfinished at observation end. Unsuccessful outcomes never receive a
successful completion timestamp. Legacy `run` still requires every task to succeed.

For each class, completed/failed/cancelled/unfinished counts are disjoint. All response-time
statistics use completed tasks only. Mean is the arithmetic mean; median averages the two middle
values for even populations; P95/P99 use nearest rank `ceil(p*n)` in sorted observations. No
completed tasks gives `N/A`. Mean and maximum queue wait use the same completed-task cohort
and require every completed task in it to have a dispatch measurement. Empty or partially
instrumented cohorts give `N/A`. The maximum is the largest dispatch-minus-arrival value.
Batch rows also retain completed and unfinished task counts. The aggregate
row pools observations across classes before calculating latency and SLA statistics.
Batch makespan is latest batch completion minus earliest batch arrival, available when at least
one batch task exists and every batch task completed.

Throughput = completed class tasks / observation-window seconds. Every class uses the same
window: workload epoch through last successful completion for a fully completed run. A resolved
dispatched run with failures or cancellations ends at the last release. Runs with unfinished tasks
use the final simulation clock, which is also the stock fallback for unsuccessful runs.
The window includes initial and intervening idle time and excludes VM creation.
A zero-duration window gives `N/A`.
For an early cutoff, future trace arrivals remain in the observations with unresolved outcomes;
they have no invented ready/start/finish events and do not contribute to completed-task latency.

## SLA classification and exact denominators

Batch has no SLA and contributes to neither successes nor misses. Interactive defaults to 5 s;
inference defaults to 10 s. For SLA-bearing tasks:

* Intrinsically feasible: CPU lower bound <= target.
* Intrinsically infeasible: CPU lower bound > target.
* Measured miss: successful finish after the absolute deadline `workloadArrival + target`.
  Comparing timestamps avoids subtractive rounding at an exact deadline.
* Potentially avoidable miss: a measured miss in an intrinsically feasible task. This is a miss
  beyond the ideal service-time lower bound, not proof of exclusive scheduler causation.

Class feasibility reports retain total tasks, SLA tasks, feasible tasks and infeasible tasks.
Feasible percentage = 100 * feasible / all SLA-bearing tasks. Unavoidable miss lower bound =
infeasible / all SLA-bearing tasks, assuming those tasks execute to successful completion under
the stated capacity constraints. Unsuccessful tasks are separately tracked, not successful SLA outcomes.

Run reports retain total measured misses, intrinsically infeasible input tasks, misses among
feasible tasks, and unresolved SLA tasks (failed, cancelled, or unfinished).
All-SLA miss ratio = measured misses / **all input SLA-bearing tasks**.
Feasible-SLA miss ratio = measured misses among feasible / **all intrinsically feasible input tasks**.
These ratios are observed completion-miss fractions, not failure-inclusive service availability.
When unresolved > 0 they are partial observations and must never be read as final success rates.
An empty denominator is `N/A`. Duplicate/missing/extra observations and mismatched task metadata
are rejected rather than silently improving results.

## Reservation occupancy and fairness

Exact-source inspection found `Vm.enableUtilizationStats()`, `getCpuUtilizationStats()`, VM state
history, host processing updates and CloudSim event listeners. `ResourceStats.add(time)` feeds
an unweighted `SummaryStatistics`; it suppresses some idle/zero transitions and filters samples
by time. Its `getMean()` is not a verified elapsed-time integral over our experiment window.
VM state history contains time, allocated/requested MIPS and migration status, not task RAM
reservations. Current RAM getters describe requested demand or allocated memory; these differ,
particularly when requests exceed VM RAM (covered by existing `RamModelTest`).

The dispatched policies integrate task PE and RAM MB reservations over the same run interval
used for throughput. Total VM PEs and total VM RAM normalize their capacity-time denominators.
The VMs have identical MIPS per PE. Idle periods contribute zero service over elapsed time.
Summing each class's share gives aggregate occupancy. This measures reserved capacity, which
includes time held before execution and release. The stock path leaves time averages unavailable.

For each class with at least one arrival within the measurement window, set
`x_c = max(class PE share, class RAM share) / entitlement(c)`.
Jain's index is `(sum x_c)^2 / (number of arrived classes * sum x_c^2)`. An arrived class
without service contributes zero. An absent class is excluded. All-zero or invalid inputs
yield `N/A`. The metric compares normalized service and reflects differences in eligible demand.
All four dispatched policies use the same entitlement defaults of 0.30 batch, 0.35
interactive and 0.35 inference. Use the same `--config` when changing the fairness reference.
FCFS and Strict Priority use weights only for reporting. Weighted DRF dispatches using
current reservations, and uses the same full-run reservation integrals for reporting.

The experiment CSV exports one aggregate row and three class rows per run, with a shared
schema for all four policies. New exports append `max_queue_wait_s` after the existing fields.
The preserved historical FCFS and WMRF-LG files retain their original headers. The CSV records
workload profiles, topology, seed, reference weights and the ledger window. Undefined metrics
and the baselines' latency and ageing parameter cells are empty.

## Calibration and experiment controls

Approved profiles/topology are unchanged. `Calibration.load` copies an existing generation
configuration and accepts only explicitly named length/target overrides. It preserves class
mix, seed, count and infrastructure. `Calibration.generateTrace(candidate, originalTrace)` copies
the exact original IDs, classes, arrivals, PEs and RAM demands and samples only changed length ranges
with a length-only seeded random stream. Unchanged length ranges retain the original sampled work,
so target-only candidates change targets alone. This avoids rejection sampling of a new length
range shifting PE/RAM draws. Regenerating a candidate directly with `WorkloadGenerator` preserves
the configured resource ranges but cannot guarantee identical realized demands; use the calibration
method for controlled comparisons. Unknown keys, invalid ranges and the
reserved name `original-stress` are rejected. Multiple candidate files can be compared:

```powershell
mvn exec:java '-Dexec.args=--feasibility experiments/service-bound-demo.properties'
# Add further .properties file paths to the same invocation (paths without spaces).
```

Candidate format (all overrides optional; name required):

```properties
name=my-unapproved-candidate
INTERACTIVE.minLengthMiPerPe=2000
INTERACTIVE.maxLengthMiPerPe=5000
INTERACTIVE.slaSeconds=5
AI_INFERENCE.minLengthMiPerPe=10000
AI_INFERENCE.maxLengthMiPerPe=10000
AI_INFERENCE.slaSeconds=10
```

These illustrative bounds are existing target * existing MIPS/PE, not new approved choices.
The checked-in `service-bound-demo` caps interactive work at 5,000 and inference at 10,000 MI/PE.
It demonstrates 100% intrinsic feasibility but does not demonstrate queueing feasibility; inference
has no slack even for simulator granularity. No candidate is automatically selected or approved.

`ExperimentRunner.Metadata` carries the explicit configuration name/purpose, policy label, complete
immutable trace (including generation configuration/seed/arrival sequence), and simulation
configuration/topology. For a candidate execution, generate its trace and use:

```java
var original = new WorkloadGenerator().generate(WorkloadGenerationConfig.defaults(WorkloadScenario.BALANCED));
var candidate = Calibration.load(java.nio.file.Path.of("experiments/service-bound-demo.properties"), original.config());
var trace = Calibration.generateTrace(candidate, original);
var metadata = new ExperimentRunner.Metadata(candidate.name(),
    ExperimentRunner.Purpose.UNAPPROVED_COMPARISON_CANDIDATE, ExperimentRunner.STOCK_POLICY,
    trace, SimulationConfig.research(trace));
var result = new ExperimentRunner().runStock(metadata);
EvaluationReport.printMetrics(result);
```

For future comparisons, create metadata per policy using the same trace/configuration objects
and different explicit policy labels. `run` first analyzes feasibility, then obtains a fresh
executor from a supplier, then collects metrics independently. Each executor must build a fresh
simulator, hosts, VMs, broker, policies, reservations and Cloudlets. The stock executor does so;
integration tests verify identical observations/metrics and distinct Cloudlet/broker instances
across repeated runs. The dispatched executor also creates a fresh policy and reservation ledger,
with repeatable placement and observed outcomes. Reuse the trace when constructing each run.
`runStock` rejects any policy label other than `ExperimentRunner.STOCK_POLICY`; a different policy
can provide its own executor to `run`. The dispatched entry points are `runWmrfLg` with policy ID
`WMRF_LG`, `runFcfs` with policy ID `FCFS` and `runStrictPriority` with policy ID
`STRICT_PRIORITY`, and `runWeightedDrf` with policy ID `WEIGHTED_DRF`.
See the [Weighted DRF design](WEIGHTED_DRF_DESIGN.md) for four-policy reproduction commands.
