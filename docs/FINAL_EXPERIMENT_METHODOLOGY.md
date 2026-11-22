# Final experiment methodology

## Design and controls

FCFS, Strict Priority, Weighted DRF and WMRF-LG each run the approved Balanced, Batch-heavy,
Interactive-heavy and Inference-heavy mixes for seeds **42 through 61 inclusive**. Each
scenario and seed has two separate strata, the approved original-stress trace and the
unapproved `service-bound-demo-unapproved` candidate. All runs have 120 tasks, a 600 s
arrival horizon and a 0.5/s arrival-rate parameter. Three hosts each have four 1,000 MIPS PEs,
16,384 MB RAM, 10,000 Mbps and 1,000,000 MB storage. The six VMs each have two 1,000 MIPS PEs,
4,096 MB RAM, 1,000 Mbps and 20,000 MB storage. Both suites share the same broker, placement,
and atomic PE/RAM admission.

Each suite has 640 runs (four policies or variants × four scenarios × two configurations × 20 seeds),
for 1,280 runs in total.
For every scenario, configuration and seed the command builds one immutable `WorkloadTrace`
and passes that object to eight independent runs. The runner creates fresh simulation,
broker, VMs, Cloudlets and policy state for each run. The command creates a new directory
and refuses to replace an existing one. The manifest records a stable
`policy/scenario/seed/configuration` identifier, fingerprint, workload controls, host and VM
specifications, source CSV and outcome. `SUCCESS` requires all tasks to finish successfully.
`INCOMPLETE` records partial counts and `ERROR` records the exception. Analysis rejects
missing cohorts, duplicate records, unmatched terms and non-success manifest rows.

## Trace fingerprint

`WorkloadTrace.fingerprint()` hashes a versioned binary serialization with SHA-256 and
exports lower-case hex as `trace_sha256`. Java `DataOutputStream` uses big-endian 32-bit
ints, 64-bit longs, 64-bit IEEE-754 doubles, one-byte booleans, and `writeUTF` strings
(two-byte length plus modified UTF-8, with ASCII tags and identifiers in this format).
The ordered fields are:

1. Format tag `group6-workload-trace-v1`, scenario name, configured task count, seed, arrival horizon and rate.
2. Class count and, in `WorkloadClass.values()` order, class name, min/max MI per PE, min/max PEs, min/max RAM,
   and an SLA-presence boolean followed by a SLA double when present.
3. Actual task count, then each task in immutable trace order. Each record contains ID, class name,
   workload arrival, MI per PE, PEs, RAM MB, optional SLA in the same encoding and generation seed.

The trace excludes the policy, outcome and topology. Run metadata records topology separately.
The fingerprint changes when actual task fields change, even for the same seed and generation
config. The seed labels identify the random replicates within each stratum.

## Ablation

Every variant runs the existing WMRF-LG implementation. Entitlements (0.30, 0.35, 0.35),
the 60 s accounting window and the 60 s ageing threshold are shared.

| Variant | Latency coefficient λ | Ageing coefficient γ |
| --- | ---: | ---: |
| `WMRF_LG_FULL` | 0.20 | 0.05 |
| `WMRF_LG_NO_LATENCY_GUARD` | 0 | 0.05 |
| `WMRF_LG_NO_AGEING` | 0.20 | 0 |
| `WMRF_LG_NO_LATENCY_NO_AGEING` | 0 | 0 |

In `score = D − λU − γA`, `U` and `A` are finite and bounded. Setting a coefficient to zero
removes that term mathematically and leaves the other term, resource accounting and tie
order intact. `WMRF_LG_FULL` reproduces standard WMRF-LG metrics and observation windows.

## Aggregation formulas

Every observation is one **run-level metric**, within workload configuration, scenario and class.
A class's within-run P95 is the sorted completed-task response at index
`ceil(0.95 × completed) − 1`; P99 uses 0.99. The reported seed mean of P95 averages
those 20 run-level P95 values. Mean first computes each run's metric and then weights
the runs equally. SLA rates use each run's denominator, so the summary is a mean of
run rates. The aggregate within each run is computed by the shared collector. For exact
event clocks, denominators and missing values see the [metric contract](EXPERIMENT_METRICS.md).

For available values `x1 … xn`, the arithmetic mean is `sum(x)/n`, sample standard
deviation is `sqrt(sum((xi − mean)^2)/(n − 1))`, and median is the middle order statistic
or the average of the two middle values. The tables also include minimum and maximum.
Blank metrics are skipped within the stratum. All unavailable strata carry `n=0` and empty
statistics. Sample SD and CI are empty for `n<2`. Status counts and denominators remain
available even when the corresponding ratio is undefined.

For paired comparisons the per-seed difference is **WMRF-LG minus baseline**, using
the same scenario, configuration, seed, trace fingerprint and class. Reported means and
medians summarize those differences. Negative favors lower latency, waiting or miss rate.
Positive favors throughput. Interpret Jain and reservation occupancy against workload demand
and the measurement objective. Ablation differences are **variant minus Full**. The factorial
interaction is **Neither − No Latency − No Ageing + Full**, formed within each seed before
averaging. A nonzero contrast signals a departure from additive effects on the measurement scale.

The pointwise 95% percentile bootstrap uses 10,000 resamples of size `n`, with replacement.
For paired comparisons and interactions it resamples the matched per-seed differences,
preserving the pairing. The generator is NumPy PCG64, seeded with **20261122**. Resampling
plans are generated for sizes 2 through 20 in ascending order and reused for groups of
equal size with observations ordered by seed. The intervals are the linear 2.5th and 97.5th
percentiles of the bootstrap means. Figures show absolute run means and these intervals.
The intervals are exploratory, with no hypothesis tests or multiplicity adjustment. They
describe variability across these workload draws, and neither model misspecification nor
other cloud topologies. Seeds and base traces are shared across comparisons and
configurations. No cross-stratum independence assumption or pooled interval is used.
[Bootstrap methods](https://doi.org/10.1214/ss/1177013815)

## Reproduction

Verification used JDK 25, Maven with Java 17 compilation target, CloudSim Plus 8.5.7,
Python 3.11 and NumPy 2.1.3 / Matplotlib 3.9.2. Commands from the repository root:

```powershell
mvn clean test
mvn test '-Dtest=FcfsPolicyTest,FcfsIntegrationTest,StrictPriorityPolicyTest,StrictPriorityIntegrationTest,WeightedDrfPolicyTest,WeightedDrfIntegrationTest,WmrfLgPolicyTest,DispatchIntegrationTest,RamModelTest,FinalExperimentTest'
mvn exec:java '-Dexec.args=--smoke'
mvn exec:java '-Dexec.args=--final-experiment target/final-reproduction experiments/service-bound-demo.properties'
mvn exec:java '-Dexec.args=--final-experiment target/final-seed42-replay experiments/service-bound-demo.properties 42'
python experiments/analysis/analyze.py target/final-reproduction --archives experiments --replay target/final-seed42-replay
git diff --check
```

For another replay, choose new destination directories under an existing parent.
The optional comma-separated seed argument is for smoke replays. The analysis command
requires the complete 42–61 sweep. It compares historical CSV rows for 42–44, validates
manifest and metrics, optionally compares every seed-42 row in both suites against an
independent replay, then writes the summaries, paired comparisons, ablation contrasts and plots
beside the simulated data. Published artifacts are under [experiments/final](../experiments/final/).
