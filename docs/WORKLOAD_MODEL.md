# Milestone 2 workload model

Group 6, Topic 22, IIIT Sri City. Infrastructure and workload ownership: Sajja Venkata Naveen.

## Experimental assumptions

These synthetic ranges are initial experimental assumptions, not measurements of a production
service. Inference is represented by CPU work and RAM demand; there is no GPU or neural-network
execution. Response targets are metadata for future policies and evaluation, not enforced deadlines.

| Class | Length (MI per PE, inclusive) | PEs | RAM (MB per task, inclusive) | Response target |
| --- | ---: | ---: | ---: | ---: |
| BATCH | 30,000–120,000 | 1–2 | 1,024–2,048 | None |
| INTERACTIVE | 2,000–10,000 | 1 | 256–1,024 | 5 s |
| AI_INFERENCE | 10,000–50,000 | 1–2 | 1,536–3,072 | 10 s |

Resource values are sampled uniformly as integers in each inclusive range. PE count and RAM demand
are sampled independently. Length is **per PE**: a 20,000-MI, two-PE task has 40,000 MI of total work,
and `CloudletSimple` receives length 20,000 and PE count 2. RAM is per task and is never multiplied by PEs.

`WorkloadProfile` and `WorkloadGenerationConfig` hold every generation parameter. Change profiles
through the Java API, without editing `WorkloadGenerator`. Batch profiles must omit the target;
interactive and inference profiles must provide a finite positive target.

## Exact workload mixes

| Scenario | Batch | Interactive | Inference | Counts for 120 tasks (B/I/AI) |
| --- | ---: | ---: | ---: | --- |
| BALANCED | 40% | 35% | 25% | 48 / 42 / 30 |
| BATCH_HEAVY | 60% | 20% | 20% | 72 / 24 / 24 |
| INTERACTIVE_HEAVY | 20% | 60% | 20% | 24 / 72 / 24 |
| INFERENCE_HEAVY | 20% | 20% | 60% | 24 / 24 / 72 |

The total task count is configurable; 120 is the default. Zero tasks is a valid empty trace.
Hamilton's largest-remainder method floors each exact quota and assigns remaining tasks in
descending fractional-remainder order. Equal remainders use `WorkloadClass` declaration order
(batch, interactive, inference). Integer arithmetic avoids floating-point rounding and overflow
for any supported `int` task count. Balanced with two tasks produces 1/1/0; batch-heavy with three
produces 2/1/0. No task is lost. Counts are exact rounded quotas, not probabilistic expectations.

## Seeded arrivals and reproducibility

Defaults: seed **42**, arrival rate **0.5 tasks/second**, horizon **600 seconds**.

1. `new Random(seed)` derives separate seeds for arrival, class-shuffling and resource streams.
2. Expand the exact scenario counts into a label list, then shuffle with the seeded class stream.
3. Draw an exponential interval `-StrictMath.log1p(-u) / rate` for every task, including the first,
   with `u` from the seeded arrival stream. Cumulative intervals give ordered non-negative arrivals.
4. Assign the shuffled labels and sample resource demands. IDs are zero-based arrival positions.
5. Validate and freeze the trace and its configuration.

This is the first N events of a homogeneous Poisson-style process. There is no fixed class priority
in arrival generation. The same seed and complete configuration reproduce exactly equal task records.
Separate streams keep arrival times identical across mixes with the same seed, rate and task count;
doubling the rate halves the arrival times. Uniform timestamps spread across the horizon would not
preserve that rate behavior and are not used.

**Horizon semantics:** the horizon is a hard upper bound on arrivals, not simulation completion time.
An arbitrary fixed count, independent rate and finite horizon cannot always all be satisfied by an
unmodified exponential process. If the Nth sampled arrival exceeds the horizon, generation throws
a clear error with the task position and seed. It returns no partial trace and never rescales, clips,
retries, changes the seed or silently drops tasks. Increase the horizon/rate or reduce the count.
For example, 120 tasks at rate 0.5 have an expected final arrival of 240 seconds, with random variation.
Horizon-overflow rejection means successful traces are conditional on fitting; use a generous horizon
to avoid selecting an appreciably truncated experimental population.

`TaskMetadata` is an immutable record: ID, class, arrival seconds, length MI per PE, PEs, RAM MB,
optional response target seconds, and generation seed. `WorkloadTrace` also retains the full immutable
configuration. Keep the complete configuration and generator version when sharing/reproducing inputs;
a seed alone does not describe an experiment. Every simulation constructs fresh mutable Cloudlets
from the same immutable records, allowing future scheduling policies to consume identical inputs.

## CloudSim Plus 8.5.7 arrival integration

The trace uses a **workload clock**, whose zero is the instant all configured VMs have been created.
This separates infrastructure initialization from the arrival process, including arrivals at time zero.
Trace times are not absolute simulator timestamps.

`SimulationController` registers `DatacenterBroker.addOnVmsCreatedListener`, records the actual
`simulation.clock()` in that callback, and submits the complete Cloudlet list there.
`CloudletFactory` sets each Cloudlet's individual `setSubmissionDelay(task.arrivalTimeSeconds())`.
No list-wide delay overrides these values. VM startup delay and network topology delay are zero
in this topology. CloudSim's built-in broker mapping and space-shared schedulers remain in use;
this milestone adds no scheduling policy.

```text
expected absolute datacenter arrival = measured workload epoch + trace arrival
actual absolute start >= absolute datacenter arrival
```

`CompletedTask.workloadEpochSeconds()` exposes the measured origin, and
`expectedDatacenterArrivalSeconds()` performs the conversion. The CLI prints the epoch and maximum
arrival error. In the verified topology the epoch is 0.1 s; it is **measured, never hard-coded**.
No delay is subtracted or clamped to pretend that VMs existed at absolute simulator time zero.
For response-time calculations later, subtract the actual absolute arrival from absolute finish,
or first convert both to workload-relative times; do not mix the two clocks.

Integration tests compare `getDcArrivalTime()` against the equation for every task in all four mixes
(tolerance 1e-8 s). Isolated tasks at 0, 0.00001, 0.05, 10 and 100 seconds also start at their expected
arrivals. A contention test verifies that queueing changes start time without changing arrival time.
Repeated runs create different Cloudlet instances with identical timings. If future integration adds
VM boot delays or a network topology, revalidate this contract: the broker can add those delays.
Additional execution tests cover arrivals separated by long idle gaps (0, 100 and 1,000 seconds)
and simultaneous arrivals with non-sequential IDs. They verify delayed tasks still complete, original
metadata is associated by ID, and two-PE Cloudlets retain the per-PE length convention.

## RAM utilization and reservation limitations

Cloudlets have a separate RAM utilization model; their default RAM model is NULL.
`CloudletFactory` explicitly installs
`new UtilizationModelDynamic(UtilizationModel.Unit.ABSOLUTE, ramDemandMb)`.
The value is MB, not a fraction or percentage. The dynamic model has no update function, so its
requested demand remains constant. A 512-MB task requests 512 MB regardless of the selected VM size.

The 8.5.7 scheduler converts absolute task demand to VM usage for accounting. Tests execute two
tasks requesting 1,536 + 512 MB on a 4,096-MB VM and observe 0.5 requested utilization and 2,048 MB
allocated during processing. VM creation initially allocates its full configured capacity; that
initial infrastructure allocation must not be mistaken for active task demand.

**This is not an independent hard memory reservation.** The chosen space-shared Cloudlet scheduler
checks free CPU PEs for admission. Another test shows two one-PE tasks requesting 3,072 MB each can
both enter execution on a two-PE, 4,096-MB VM. Requested utilization is 1.5 even though physical
allocation is capped. CloudSim can apply its simplified virtual-memory/swap behavior when demand
exceeds available RAM. Therefore concurrency, completion times and throughput may reflect that
built-in behavior; they do not demonstrate a RAM-constrained admission policy.

Task feasibility validation rejects a single task larger than its VM. Host VM placement reserves
VM RAM within host capacity. Neither check guarantees that the sum of concurrently executing task
RAM demands fits the VM. `RamAdmissionLedger` is a scheduler-independent extension contract
(`tryReserve(vmId, vmCapacityMb, task)` / `release(vmId, taskId)`) for a later implementation. Future
integration must atomically reserve metadata MB before dispatch and release on completion/cancellation.
No ledger implementation or WMRF-LG reservation algorithm is installed here.

Milestone 1's fractional demands have been converted to nearest integer MB on its original 4,096-MB
VM: 15% → 614 MB, 10% → 410 MB, 55% → 2,253 MB. Its lengths, PEs, capacities, three-task completion
and concurrent execution are retained. Its smoke profiles intentionally differ from research ranges.

## Infrastructure

| Resource | Each host | Each VM |
| --- | ---: | ---: |
| Count | 3 | 6 |
| PEs | 4 | 2 |
| MIPS per PE | 1,000 | 1,000 |
| RAM (MB) | 16,384 | 4,096 |
| Bandwidth (Mbps) | 10,000 | 1,000 |
| Storage (MB) | 1,000,000 | 20,000 |

There are 12 host PEs and 12 VM PEs; two VMs fit each host. Host CPU allocation is explicitly
`VmSchedulerSpaceShared`. Each VM has a fresh `CloudletSchedulerSpaceShared`. Configurable counts
and `MachineConfig` capacities are constructor inputs. Validation computes per-host VM slots as
the minimum of the integer CPU, RAM, bandwidth and storage ratios, then multiplies by host count.
It also checks per-PE MIPS. This rejects impossible per-host packing even when aggregate totals fit.
No host resource is automatically overcommitted.

The original one-host, one-VM configuration remains available through `SimulationConfig.milestoneOne()`.

## Validation and usage

Constructors reject invalid ranges, non-finite/negative arrivals, non-positive resource demands,
invalid targets, missing profiles and invalid rates/horizons. `WorkloadValidator` additionally checks
unique IDs, ordered arrivals, exact count, exact scenario distribution, profile membership, matching
seeds and horizon bounds. `SimulationConfig` validates topology packing and per-task VM feasibility.
Lists/maps are defensively copied. No CloudSim object is required to generate or validate a trace.

Run from the repository root in PowerShell:

```powershell
mvn clean test
mvn exec:java
mvn exec:java '-Dexec.args=BATCH_HEAVY 120 42 600 0.5'
mvn exec:java '-Dexec.args=INTERACTIVE_HEAVY 120 42 600 0.5'
mvn exec:java '-Dexec.args=INFERENCE_HEAVY 120 42 600 0.5'
mvn exec:java '-Dexec.args=BALANCED 120 42 600 0.5'
mvn exec:java '-Dexec.args=--smoke'
```

Positional arguments: scenario, task count, seed, horizon seconds, rate tasks/second. Omitted trailing
arguments use defaults. Repeat a command to regenerate the identical trace. The CLI also generates
twice and checks record equality, prints actual class/resource ranges, runs CloudSim, and reports
successful Cloudlets and arrival error.

Custom profiles/topology are available through Java:

```java
var profiles = new java.util.EnumMap<WorkloadClass, WorkloadProfile>(WorkloadClass.class);
profiles.putAll(WorkloadGenerationConfig.defaultProfiles());
profiles.put(WorkloadClass.INTERACTIVE,
    new WorkloadProfile(2_000, 8_000, 1, 1, 256, 768, java.util.OptionalDouble.of(4)));
var config = new WorkloadGenerationConfig(WorkloadScenario.BALANCED, 120, 42, 600, 0.5, profiles);
var trace = new WorkloadGenerator().generate(config);
var results = new SimulationController().run(SimulationConfig.research(trace));
// Or pass custom MachineConfig objects and host/VM counts to new SimulationConfig(..., trace.tasks()).
```

The relevant types are in `com.group6.cloudsim.workload` and `com.group6.cloudsim.core`.
No baseline policy or fairness/latency algorithm is added. Milestone 3A evaluation modules and
their measurement limits are documented in [EXPERIMENT_METRICS.md](EXPERIMENT_METRICS.md).
See [verification results](MILESTONE_2_VERIFICATION.md) for the actual executed runs.

## Milestone 3A stress profiles and calibration

The approved profiles above remain the original stress configuration. At 1,000 MIPS/PE,
interactive work above 5,000 MI/PE and inference work above 10,000 MI/PE already exceed their
5/10-second targets without any queueing. PE count scales total work and total assigned capacity
together; it does not divide per-PE service time. Batch tasks have no response-time SLA.

Run `mvn exec:java '-Dexec.args=--feasibility'` for per-class/task-derived feasibility in every scenario.
Supply one or more candidate property files after `--feasibility` to compare explicit named
alternatives. `experiments/service-bound-demo.properties` is an unapproved mathematical example,
not a selected comparison workload. `Calibration` copies configuration/profile maps and changes only
explicit length/target fields. Seed, arrivals, class mix, PEs, RAM and topology remain fixed.
Candidate run metadata must retain the candidate name and `UNAPPROVED_COMPARISON_CANDIDATE` purpose;
stress runs use `original-stress` and `ORIGINAL_STRESS`. No approved comparison values are selected here.

## Exact-version references

- [SubmissionDelayable 8.5.7](https://javadoc.io/static/org.cloudsimplus/cloudsimplus/8.5.7/org/cloudsimplus/core/SubmissionDelayable.html)
- [DatacenterBroker 8.5.7](https://javadoc.io/static/org.cloudsimplus/cloudsimplus/8.5.7/org/cloudsimplus/brokers/DatacenterBroker.html)
- [Cloudlet 8.5.7](https://javadoc.io/static/org.cloudsimplus/cloudsimplus/8.5.7/org/cloudsimplus/cloudlets/Cloudlet.html)
- [UtilizationModelDynamic 8.5.7](https://javadoc.io/static/org.cloudsimplus/cloudsimplus/8.5.7/org/cloudsimplus/utilizationmodels/UtilizationModelDynamic.html)
- [Published 8.5.7 sources](https://repo.maven.apache.org/maven2/org/cloudsimplus/cloudsimplus/8.5.7/cloudsimplus-8.5.7-sources.jar):
  `DatacenterBrokerAbstract` (relative delay and VM-created callback), `CloudletSchedulerAbstract`
  (RAM accounting and oversubscription), and `CloudletSchedulerSpaceShared` (CPU admission).

The published sources were inspected alongside the exact binary and execution tests. Some Javadoc
pages could not be fetched by the browsing tool; the Maven source artifact supplied those details.
