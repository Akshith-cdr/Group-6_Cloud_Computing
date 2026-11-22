# Milestone 3A metric contract and readiness

## Status and prerequisite audit

This checkout is based on `main` commit `160faf9`. All three remote branches were inspected.
The prompt's local, uncommitted arrival-driven framework is not in any of them:
`DispatchBroker`, `DispatchPolicy`, `DispatchCloudletScheduler`, and `VmReservations` are absent.
The actual baseline has 43 tests, a `DatacenterBrokerSimple`, and an interface-only RAM ledger.
This change adds verified feasibility and metric definitions without reconstructing that missing
framework or implementing FCFS, priority, weighted DRF, or WMRF-LG.

**Policy comparisons are not ready.** Ready-entry/dispatch instrumentation and enforced concurrent
RAM admission still require the arrival-driven integration. CPU and RAM time averages are also
unavailable. The stock execution path reports these limitations rather than substituting timestamps.

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
| CloudSim submission | clock at the actual `broker.submitCloudletList` call; currently the epoch, including future tasks |
| Execution start | `Cloudlet.getStartTime()`, absent when its value is unassigned |
| Completion | `Cloudlet.getFinishTime()` only when status is `SUCCESS` |

`getDcArrivalTime()` is the separate datacenter-receipt timestamp, already checked by existing tests
against epoch + trace arrival. It must not replace workload arrival or policy dispatch. In
`DatacenterBrokerAbstract.createWaitingCloudlets`, VM mapping happens in advance and
`CLOUDLET_SUBMIT` is sent with a future submission delay. Neither the submission API time nor
datacenter receipt is an arrival-driven dispatch event. Unknown timestamps use empty
`OptionalDouble`; CLI fields display `N/A`, never a fabricated zero.

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
completed tasks gives `N/A`. Average queue wait uses all completed tasks only if every completed
task has a dispatch measurement; a partially instrumented class gives `N/A`.

Throughput = completed class tasks / observation-window seconds. Every class uses the same
window: workload epoch through last successful completion for a fully completed run; otherwise
through simulation observation end. Idle gaps and the initial wait for arrivals are included,
and VM creation is excluded. Fully completed runs exclude final shutdown grace; incomplete runs
use the explicit observation end, which the stock controller takes from the final simulation clock.
A zero-duration window gives `N/A`.
For an early cutoff, future trace arrivals remain in the observations with unresolved outcomes;
they have no invented ready/start/finish events and do not contribute to completed-task latency.

## SLA classification and exact denominators

Batch has no SLA and contributes to neither successes nor misses. Interactive defaults to 5 s;
inference defaults to 10 s. For SLA-bearing tasks:

* Intrinsically feasible: CPU lower bound <= target.
* Intrinsically infeasible: CPU lower bound > target.
* Measured miss: successfully completed response time > target.
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

## Infrastructure utilization investigation

Exact-source inspection found `Vm.enableUtilizationStats()`, `getCpuUtilizationStats()`, VM state
history, host processing updates and CloudSim event listeners. `ResourceStats.add(time)` feeds
an unweighted `SummaryStatistics`; it suppresses some idle/zero transitions and filters samples
by time. Its `getMean()` is not a verified elapsed-time integral over our experiment window.
VM state history contains time, allocated/requested MIPS and migration status, not task RAM
reservations. Current RAM getters describe requested demand or allocated memory; these differ,
particularly when requests exceed VM RAM (covered by existing `RamModelTest`).

A later validated observer must integrate `sum(u_i * (t_next - t_i)) / windowDuration` across
all state changes, including idle periods and the final boundary. Aggregate CPU should weight
VMs by MIPS capacity; aggregate RAM should weight by RAM capacity. Requested demand, allocated
memory and reservation occupancy require separate labels. No time-average CPU/RAM metric is
published here, and no single instantaneous snapshot is labeled an average. Jain fairness,
resource-time accounting and failure-inclusive availability remain future measurements.

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
across repeated runs. Never reuse a mutable executor or Cloudlet. Future dispatch adapters must
capture real ready/dispatch events and failure/cancellation release behavior before comparisons.
`runStock` rejects any policy label other than `ExperimentRunner.STOCK_POLICY`; a different policy
must provide its own executor to `run`. Reporting does not label custom executors as stock brokers.
