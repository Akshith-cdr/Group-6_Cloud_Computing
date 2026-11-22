# Milestone 3A verification and integration handoff

The November 19 sections below record the original review. [Final core milestone verification](#final-core-milestone-verification)
records the subsequent WMRF-LG checks. [FCFS baseline verification](#fcfs-baseline-verification)
records the FCFS checks. [Strict Priority baseline verification](#strict-priority-baseline-verification)
records the three-policy controls and Batch waiting results. [Weighted DRF baseline verification](#weighted-drf-baseline-verification)
records the current-reservation checks and four-policy experiment controls.

Verified on 19 November 2026 on `feature/aswarth-m3a-evaluation`, based on `160faf9`.
The actual remote baseline has 43 tests. It does not contain the prompt's local/uncommitted
53-test arrival-driven dispatch framework. All remote branches were inspected:
`main`, `feature/akshith-m1-foundation`, and `feature/naveen-workloads`.

## Executed verification

Environment: Windows, Microsoft OpenJDK 21.0.9 and Maven 3.9.9 downloaded into ignored `.tools/`.
Project compilation remains Java release 17; CloudSim Plus remains exactly 8.5.7.
No project dependency was added. Exact published CloudSim sources were inspected locally.

| Check | Result |
| --- | --- |
| Existing baseline tests after observation refactor | 43 passed |
| Final `mvn clean test` | 66 passed, 0 failed/errors/skipped |
| Evaluation tests | 23 passing invocations; all 43 prior tests preserved |
| Milestone 1 smoke CLI | 3/3 successful; epoch 0.1 s; zero arrival error |
| Default balanced CLI | 120/120 successful; epoch 0.1 s; zero arrival error |
| Other three default scenario CLIs | 120/120 successful each; zero arrival error |
| Feasibility CLI, original profiles | all four scenarios reported below |
| Feasibility CLI, named alternative | all four scenarios; no intrinsic infeasibility |
| `git diff --check` | passed |

The test suite includes actual simulations for all four default scenarios. New tests cover
per-PE ideal times; fastest eligible VM and RAM/PE eligibility; equality/infeasibility; batch
exclusion; SLA denominators; failures/cancellations/unfinished outcomes; workload-clock response;
dispatch-based queue wait; pre-execution/service decomposition; missing/duplicate observations;
percentiles; empty classes/windows; deterministic reports; original profiles/topology;
candidate isolation; target calibration; unknown-key rejection; fresh simulation instances;
missing-dispatch markers; and original smoke timing.

The initial new smoke assertion assumed continuous-time completion at 20.1 s. Actual CloudSim
completion is 20.21 s (20.11 s elapsed service), so verification was corrected to distinguish
the CPU lower bound from event-granularity overhead. Final regression asserts actual smoke finishes
at 20.21 / 1.10 / 6.21 s for batch / interactive / inference, with all starts at 0.1 s.

## Original-stress feasibility, seed 42

Every row includes class total, SLA-bearing count, intrinsically feasible/infeasible counts,
feasible percentage and unavoidable-miss lower bound. Batch has no SLA; its percentages are N/A.

| Scenario | Class | Total | SLA | Feasible | Infeasible | Feasible % | Miss lower bound |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| BALANCED | Batch | 48 | 0 | 0 | 0 | N/A | N/A |
| BALANCED | Interactive | 42 | 42 | 16 | 26 | 38.095238 | 0.619048 |
| BALANCED | Inference | 30 | 30 | 0 | 30 | 0 | 1 |
| BATCH_HEAVY | Batch | 72 | 0 | 0 | 0 | N/A | N/A |
| BATCH_HEAVY | Interactive | 24 | 24 | 8 | 16 | 33.333333 | 0.666667 |
| BATCH_HEAVY | Inference | 24 | 24 | 0 | 24 | 0 | 1 |
| INTERACTIVE_HEAVY | Batch | 24 | 0 | 0 | 0 | N/A | N/A |
| INTERACTIVE_HEAVY | Interactive | 72 | 72 | 32 | 40 | 44.444444 | 0.555556 |
| INTERACTIVE_HEAVY | Inference | 24 | 24 | 0 | 24 | 0 | 1 |
| INFERENCE_HEAVY | Batch | 24 | 0 | 0 | 0 | N/A | N/A |
| INFERENCE_HEAVY | Interactive | 24 | 24 | 14 | 10 | 58.333333 | 0.416667 |
| INFERENCE_HEAVY | Inference | 72 | 72 | 0 | 72 | 0 | 1 |

The explicit `service-bound-demo-unapproved` candidate changes only interactive maximum length
to 5,000 MI/PE and inference length to 10,000 MI/PE. These values come from the existing targets
times existing VM MIPS/PE. It retains every other control, including batch ranges and all targets.

| Candidate scenario | Batch total/no SLA | Interactive feasible / SLA | Inference feasible / SLA |
| --- | ---: | ---: | ---: |
| BALANCED | 48 | 42 / 42 | 30 / 30 |
| BATCH_HEAVY | 72 | 24 / 24 | 24 / 24 |
| INTERACTIVE_HEAVY | 24 | 72 / 72 | 24 / 24 |
| INFERENCE_HEAVY | 24 | 24 / 24 | 72 / 72 |

For the candidate, every interactive/inference class has 100% intrinsic feasibility and lower
bound 0. Batch remains N/A. This is only a calibration demonstration, not an approved comparison
configuration or evidence of meeting measured response SLAs. Inference has no CPU slack.

## Default balanced measured completion metrics

The stock run is labeled `original-stress / ORIGINAL_STRESS / cloudsim-stock-space-shared`.
Common observation window: simulation time 0.100000 through 764.481000 seconds.
All failed, cancelled, unfinished and unresolved-SLA counts are zero.

| Class | Completed | Mean response s | Median s | P95 s | P99 s | Throughput tasks/s |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Batch | 48 | 226.050031 | 180.909913 | 492.398129 | 592.702492 | 0.062796 |
| Interactive | 42 | 118.156956 | 50.168399 | 497.338871 | 512.808733 | 0.054946 |
| Inference | 30 | 187.838122 | 128.526938 | 453.347072 | 461.870332 | 0.039247 |

| Class | SLA tasks | Intrinsically infeasible | Measured misses | Misses among feasible | Miss / all SLA | Miss / feasible |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Batch | 0 | 0 | 0 | 0 | N/A | N/A |
| Interactive | 42 | 26 | 37 | 11 | 0.880952 | 0.687500 |
| Inference | 30 | 30 | 30 | 0 | 1.000000 | N/A |

Queue wait and pre-execution delay are N/A because no arrival-driven dispatch event exists here.
These are stock CloudSim results, not a new baseline scheduling algorithm or a fair policy comparison.

## Metric formulas and unresolved integration

`response = finish - (epoch + trace arrival)`; `queue wait = dispatch - workload arrival`;
`pre-execution delay = start - dispatch`; `service = finish - start`. Start/finish come from
Cloudlet getters, completion is success-only, and CloudSim API submission is measured at the
epoch callback. Ready/dispatch values are explicitly unobserved. Every ratio denominator,
percentile definition, outcome rule and time window is in [EXPERIMENT_METRICS.md](EXPERIMENT_METRICS.md).

CloudSim `ResourceStats.getMean()` is an unweighted sample mean with idle filtering, not a verified
time-weighted mean over this window. CPU/RAM time averages are withheld pending validated
integration over state transitions. Requested RAM, allocation and reservations are distinct.
The missing dispatch framework must supply real queue/dispatch events and hard admission before
policy comparisons. No FCFS, strict priority, weighted DRF or WMRF-LG was implemented.

## Reproduction

With Java and Maven on PATH, from the repository root:

```powershell
mvn clean test
mvn exec:java '-Dexec.args=--smoke'
mvn exec:java
mvn exec:java '-Dexec.args=--feasibility'
mvn exec:java '-Dexec.args=--feasibility experiments/service-bound-demo.properties'
git diff --check
```

This session used the same goals with a repository-local toolchain and dependency cache:

```powershell
$env:JAVA_HOME=(Get-Item '.tools\jdk-21.0.9+10').FullName
$env:Path="$env:JAVA_HOME\bin;$env:Path"
& .\.tools\apache-maven-3.9.9\bin\mvn.cmd '-Dmaven.repo.local=.tools/m2' clean test
& .\.tools\apache-maven-3.9.9\bin\mvn.cmd '-Dmaven.repo.local=.tools/m2' exec:java '-Dexec.args=--smoke'
& .\.tools\apache-maven-3.9.9\bin\mvn.cmd '-Dmaven.repo.local=.tools/m2' exec:java
& .\.tools\apache-maven-3.9.9\bin\mvn.cmd '-Dmaven.repo.local=.tools/m2' exec:java '-Dexec.args=--feasibility'
& .\.tools\apache-maven-3.9.9\bin\mvn.cmd '-Dmaven.repo.local=.tools/m2' exec:java '-Dexec.args=--feasibility experiments/service-bound-demo.properties'
```

Local execution logs are ignored under `.tools/`; final test XML lives under ignored
`target/surefire-reports/`. No downloaded runtime, cache, generated Cloudlet or secret is committed.

## Files added and modified

Added under `src/main/java/com/group6/cloudsim/evaluation/`:
`SlaFeasibilityAnalyzer.java`, `TaskObservation.java`, `MetricsCollector.java`, `Calibration.java`,
`ExperimentRunner.java`, and `EvaluationReport.java`.
Also added `src/test/java/com/group6/cloudsim/evaluation/EvaluationTest.java`,
`experiments/service-bound-demo.properties`, `docs/EXPERIMENT_METRICS.md`, and this verification report.

Modified `.gitignore`, `README.md`, `src/main/java/com/group6/cloudsim/Main.java`,
`src/main/java/com/group6/cloudsim/core/SimulationController.java`, `docs/WORKLOAD_MODEL.md`,
`docs/MILESTONE_2_VERIFICATION.md`, and `docs/CONTRIBUTION_PLAN.md`.
Original workload, topology, dependency declarations and existing test files are unchanged.

## Follow-up review on 19 November 2026

Seven regression tests were added in
`src/test/java/com/group6/cloudsim/evaluation/EvaluationReviewTest.java`. They reproduced gaps in
the first PR revision: an incorrect workload clock was accepted, unfinished future arrivals were
rejected at an early cutoff, events after the cutoff were accepted on unfinished tasks, ready/API
submission timestamps could follow execution when dispatch was unknown, and a stock run could
carry another policy's label. The collector/observation validation and stock-run guard now reject
those invalid cases while preserving unresolved future arrivals.

The review also reproduced a calibration problem with a wide candidate length range: rejection
sampling changed a later RAM demand from 293 to 412 MB. The original generator and approved
profiles remain untouched. Instead, `Calibration.generateTrace` copies exact original task controls
and resource demands and samples changed length ranges separately. CLI and documented candidate
execution now use this path. Tests verify deterministic calibration, retained PE/RAM/arrival samples,
unchanged work for target-only calibration and rejection of mismatched source controls.

The final test run passes 66 tests. Smoke and all four default CLIs complete with zero arrival
error. A single feasibility invocation compares both `service-bound-demo-unapproved` and a temporary
target-only candidate (10 s interactive / 50 s inference, derived from original maximum work / VM
capacity) across all four scenarios. Both have 100% intrinsic feasibility for SLA tasks. The original
feasibility counts and balanced measured results above remain unchanged. The temporary file is
ignored under `.tools/target-candidate.properties`; reproduce its comparison with:

```powershell
@('name=target-demo-unapproved','INTERACTIVE.slaSeconds=10','AI_INFERENCE.slaSeconds=50') |
    Set-Content "$env:TEMP\group6-target-candidate.properties"
mvn exec:java "-Dexec.args=--feasibility experiments/service-bound-demo.properties $env:TEMP/group6-target-candidate.properties"
```

If the temporary directory contains spaces, place the candidate under a path without spaces for
this Maven argument example. Both candidates remain unapproved examples. GitHub reports no merge
conflict on PR #4; no remote CI checks or reviews are configured/reported at this time. Local checks
do not resolve the missing dispatch/admission framework, so the PR remains a draft.

## Final core milestone verification

Verified on 22 November 2026 on `feature/akshith-wmrf-lg` from PR #4 merge `3585428`.
Environment was Windows with Oracle JDK 25, Maven 3.10.1, Java release 17 compilation and
CloudSim Plus 8.5.7. The [audit](MILESTONE_3A_AUDIT.md) records the independent findings.

| Check | Result |
| --- | --- |
| Untouched merge test suite | 66 passed before edits |
| `mvn clean test` | 85 passed, 0 failed, 0 errors, 0 skipped |
| Focused policy, dispatch, fairness and deadline tests | 19 passed |
| Stock smoke and default balanced | 3/3 and 120/120 completed, with zero datacenter-arrival error |
| Feasibility CLI | Four original-stress scenarios and four named demonstration candidates |
| WMRF-LG sweep | 24 runs, 2,880 completions, zero failures, cancellations or unfinished tasks |
| CSV replay | 96 data rows, byte-identical on an independent sweep |
| `git diff --check` | Passed |

The sweep used seeds 42, 43 and 44 in each of the four scenarios, with the original stress
profiles and a paired `service-bound-demo-unapproved` trace for every seed. All candidate SLA
tasks were intrinsically feasible. Candidate observed miss ratios ranged from 37.5% to 91.7%,
reflecting queueing and execution effects. These runs characterize WMRF-LG on the specified inputs.
Comparable FCFS, strict-priority and weighted DRF experiments remain pending.

### Original-stress ranges across the three seeds

| Scenario | Throughput (tasks/s) | SLA misses | Misses on intrinsically feasible tasks | Jain index |
| --- | ---: | ---: | ---: | ---: |
| BALANCED | 0.187–0.198 | 62–65 / 72 | 6–12 | 0.489–0.531 |
| BATCH_HEAVY | 0.146–0.164 | 44–46 / 48 | 5–6 | 0.421–0.438 |
| INTERACTIVE_HEAVY | 0.248–0.331 | 76–78 / 96 | 7–15 | 0.616–0.712 |
| INFERENCE_HEAVY | 0.214–0.241 | 86–93 / 96 | 2–9 | 0.691–0.694 |

The [CSV](../experiments/wmrf-lg-results.csv) includes class and aggregate mean, median,
P95 and P99 response times, queue waits, throughput, batch makespan, CPU and RAM reservation
occupancy, outcome counts and the exact SLA denominators per seed and configuration.

```powershell
mvn clean test
mvn test '-Dtest=WmrfLgPolicyTest,DispatchIntegrationTest,FairnessCalculatorTest,MetricBoundaryTest'
mvn exec:java '-Dexec.args=--smoke'
mvn exec:java
mvn exec:java '-Dexec.args=--feasibility'
mvn exec:java '-Dexec.args=--feasibility experiments/service-bound-demo.properties'
mvn exec:java '-Dexec.args=--experiment WMRF_LG experiments/wmrf-lg-results.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
mvn exec:java '-Dexec.args=--experiment WMRF_LG target/wmrf-lg-replay.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
Get-FileHash experiments/wmrf-lg-results.csv, target/wmrf-lg-replay.csv
git diff --check
```

Both exports had SHA-256 `EEBE6A0A048111590C5001E15366EB49DCB65BF1F32B34859B79CB80F9731D34`.
No commit, push or merge was performed during the final core milestone.

## FCFS baseline verification

The starting branch was `feature/akshith-wmrf-lg` at `3585428`, with the WMRF-LG
implementation in the worktree. FCFS runs through the same broker, VM placement and
reservation pipeline. See the [FCFS design](FCFS_DESIGN.md).

| Check | Result |
| --- | --- |
| `mvn clean test` | 95 passed, 0 failures, 0 errors, 0 skipped |
| Focused FCFS tests | 10 passed, including CPU and RAM contention, feasible-task bypass, and terminal release |
| Existing WMRF-LG and dispatch tests | 15 passed in the focused run |
| Stock smoke | 3/3 completed, with zero datacenter-arrival error |
| FCFS default balanced trace | 120/120 completed using seed 42 |
| FCFS and WMRF-LG sweeps | 24 runs per policy with 2,880 completions each, zero failed, cancelled or unfinished tasks |
| CSV replay and controls | 96 data rows per policy, matching schema and input metadata, byte-identical FCFS replay and WMRF-LG control |
| `git diff --check` | Passed |

The sweeps used seeds 42, 43 and 44 in each scenario with original-stress and
`service-bound-demo-unapproved`. One integration test passes the same immutable trace instance
to both schedulers and verifies equal feasibility results. The sweep checks matched workloads,
topology, entitlement weights and SLA denominators in every paired CSV row. All SLA tasks in the
named candidate were intrinsically feasible. Observed FCFS candidate miss ratios ranged
from 54.2% to 97.9%.

| FCFS original-stress scenario | Throughput (tasks/s) | SLA misses | Misses on intrinsically feasible tasks | Jain index |
| --- | ---: | ---: | ---: | ---: |
| BALANCED | 0.199–0.205 | 69–71 / 72 | 13–16 | 0.489–0.531 |
| BATCH_HEAVY | 0.144–0.165 | 47–48 / 48 | 7–10 | 0.421–0.438 |
| INTERACTIVE_HEAVY | 0.270–0.340 | 81–85 / 96 | 12–22 | 0.616–0.712 |
| INFERENCE_HEAVY | 0.215–0.247 | 92–96 / 96 | 8–11 | 0.691–0.694 |

The full-run Jain index can be similar across policies when the same classes complete
similar reservation work. Latency and queue times capture the effects of dispatch order.
See [FCFS results](../experiments/fcfs-results.csv) and [WMRF-LG results](../experiments/wmrf-lg-results.csv).

```powershell
mvn clean test
mvn test '-Dtest=FcfsPolicyTest,FcfsIntegrationTest'
mvn test '-Dtest=WmrfLgPolicyTest,DispatchIntegrationTest'
mvn exec:java '-Dexec.args=--smoke'
mvn exec:java '-Dexec.args=--experiment FCFS target/fcfs-balanced.csv --scenario BALANCED --seeds 42'
mvn exec:java '-Dexec.args=--experiment FCFS experiments/fcfs-results.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
mvn exec:java '-Dexec.args=--experiment WMRF_LG target/fcfs-wmrf-control.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
mvn exec:java '-Dexec.args=--experiment FCFS target/fcfs-replay.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
Get-FileHash experiments/fcfs-results.csv, target/fcfs-replay.csv
Get-FileHash experiments/wmrf-lg-results.csv, target/fcfs-wmrf-control.csv
git diff --check
```

The two FCFS exports had SHA-256 `94CF05B47A892900AC7A6D6ACAB0D32D6923FEE4C611918F7C11E6EE43F2864F`.
The WMRF-LG control matched the prior export byte for byte.

## Strict Priority baseline verification

The worktree started on `feature/akshith-wmrf-lg` at `3585428` with the WMRF-LG and
FCFS implementations uncommitted. This revision retains their dispatch policies and broker,
VM placement and reservations. It adds the fixed-class strategy and the shared maximum
queue-wait metric. See the [Strict Priority design](STRICT_PRIORITY_DESIGN.md).

| Check | Result |
| --- | --- |
| `mvn clean test` | 111 passed, 0 failures, 0 errors, 0 skipped |
| Focused Strict Priority tests | 16 passed |
| Focused FCFS, WMRF-LG, dispatcher and RAM tests | 27 passed |
| Stock smoke | 3/3 completed, zero datacenter-arrival error |
| Strict Priority default balanced trace | 120/120 completed using seed 42 |
| Strict Priority, FCFS and WMRF-LG sweeps | 24 runs and 2,880 completions per policy, zero failed, cancelled or unfinished tasks |
| CSV controls and replay | 96 rows per policy, matched inputs and SLA denominators, byte-identical Strict Priority replay |
| `git diff --check` | Passed |

In the controlled test, one Batch task and eight Interactive tasks each needed the entire
two-PE VM. The Batch task waited **8.770 simulated seconds** while the Interactive queue
used eight successive admissions. It dispatched at the final Interactive completion and
finished successfully. This demonstrates repeated deferral under contention in a finite trace.

Each sweep used seeds 42, 43 and 44 for original stress and the unapproved, intrinsically feasible
demonstration candidate. An integration test gives all three policies the same immutable trace
and checks the observations, feasibility report and distinct Cloudlet instances. All sweep
rows matched on input metadata, topology, entitlement weights and SLA denominators. The
FCFS and WMRF-LG control exports reproduced every historical column exactly. Their original
CSV files were preserved. New exports use the common schema with `max_queue_wait_s` appended.

| Strict Priority original stress | Mean Batch queue wait (s) | Maximum Batch queue wait (s) | Batch makespan (s) |
| --- | ---: | ---: | ---: |
| BALANCED | 118.210–150.643 | 325.071–368.509 | 588.828–647.386 |
| BATCH_HEAVY | 175.351–209.154 | 446.449–511.687 | 728.516–824.534 |
| INTERACTIVE_HEAVY | 38.652–74.730 | 140.045–206.870 | 354.207–466.387 |
| INFERENCE_HEAVY | 95.505–182.971 | 308.900–382.794 | 498.450–599.728 |

The ranges span the three seeds in each scenario. The export includes both configurations, all
class and aggregate metrics, and Batch outcomes. See the [Strict Priority results](../experiments/strict-priority-results.csv).

```powershell
mvn clean test
mvn test '-Dtest=StrictPriorityPolicyTest,StrictPriorityIntegrationTest'
mvn test '-Dtest=FcfsPolicyTest,FcfsIntegrationTest,WmrfLgPolicyTest,DispatchIntegrationTest,RamModelTest'
mvn exec:java '-Dexec.args=--smoke'
mvn exec:java '-Dexec.args=--experiment STRICT_PRIORITY target/strict-priority-balanced.csv --scenario BALANCED --seeds 42'
mvn exec:java '-Dexec.args=--experiment STRICT_PRIORITY experiments/strict-priority-results.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
mvn exec:java '-Dexec.args=--experiment FCFS target/strict-priority-fcfs-control.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
mvn exec:java '-Dexec.args=--experiment WMRF_LG target/strict-priority-wmrf-control.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
mvn exec:java '-Dexec.args=--experiment STRICT_PRIORITY target/strict-priority-replay.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
Get-FileHash experiments/strict-priority-results.csv, target/strict-priority-replay.csv
git diff --check
```

Both Strict Priority exports had SHA-256 `FD6CFF9077A1BDA445AE4BCB379E2089D0B5378B4530E3838660AFFD170349F5`.

## Weighted DRF baseline verification

The worktree started on `feature/akshith-wmrf-lg` at `3585428` with WMRF-LG, FCFS and
Strict Priority uncommitted. The baseline adds current allocation snapshots from the shared
reservation ledger. Its comparator uses no rolling service, urgency or ageing. See the
[Weighted DRF design](WEIGHTED_DRF_DESIGN.md).

| Check | Result |
| --- | --- |
| `mvn clean test` | 125 passed, 0 failures, 0 errors, 0 skipped |
| Focused Weighted DRF unit and integration tests | 14 passed |
| Focused existing policies, dispatcher and RAM tests | 43 passed |
| Stock smoke | 3/3 completed, zero datacenter-arrival error |
| Weighted DRF default balanced trace | 120/120 completed using seed 42 |
| Custom entitlement CLI check | 120/120 completed, weights 0.50/0.25/0.25 recorded and mean queue wait changed |
| Four-policy sweeps | 24 runs and 2,880 completions each, with zero failures, cancellations or unfinished tasks |
| CSV checks | 96 data rows per policy, matching schema, input metadata and SLA denominators |
| Deterministic replay | Weighted DRF CSV byte-identical |
| `git diff --check` | Passed |

The integration tests observe each instantaneous allocation during one dispatch event,
sum active reservations across two VMs, and check that completion, failure and cancellation
remove the released resources. A four-policy integration test uses one immutable trace
and verifies distinct Cloudlets, observed task metadata and matching feasibility results.
For the sweeps, all four CLIs used the same deterministic generator, original-stress and
unapproved demonstration configurations, topology, and seeds 42, 43 and 44. All candidate
SLA tasks were intrinsically feasible. The previous three result files kept their hashes.
The FCFS and WMRF-LG controls reproduced all historical fields, and the Strict Priority
control matched its original CSV byte for byte.

| Weighted DRF original stress | Throughput (tasks/s) | SLA misses | Misses on intrinsically feasible tasks | Mean Batch wait (s) | Maximum Batch wait (s) |
| --- | ---: | ---: | ---: | ---: | ---: |
| BALANCED | 0.187–0.203 | 66–68 / 72 | 10–14 | 125.563–149.762 | 328.986–359.826 |
| BATCH_HEAVY | 0.145–0.166 | 45–46 / 48 | 5–8 | 179.369–208.265 | 435.454–513.763 |
| INTERACTIVE_HEAVY | 0.250–0.336 | 77–81 / 96 | 12–16 | 39.649–76.338 | 141.392–212.153 |
| INFERENCE_HEAVY | 0.200–0.244 | 89–93 / 96 | 5–8 | 57.813–118.180 | 212.925–316.002 |

Ranges span the three seeds. The [Weighted DRF CSV](../experiments/weighted-drf-results.csv)
includes both configurations, all class and aggregate metrics, reservation occupancies, Batch
makespan and Jain fairness. The heuristic and results are specific to the tested traces and
topology.

```powershell
mvn clean test
mvn test '-Dtest=WeightedDrfPolicyTest,WeightedDrfIntegrationTest'
mvn test '-Dtest=FcfsPolicyTest,FcfsIntegrationTest,StrictPriorityPolicyTest,StrictPriorityIntegrationTest,WmrfLgPolicyTest,DispatchIntegrationTest,RamModelTest'
mvn exec:java '-Dexec.args=--smoke'
mvn exec:java '-Dexec.args=--experiment WEIGHTED_DRF target/weighted-drf-balanced.csv --scenario BALANCED --seeds 42'
mvn exec:java '-Dexec.args=--experiment WEIGHTED_DRF experiments/weighted-drf-results.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
mvn exec:java '-Dexec.args=--experiment FCFS target/weighted-drf-fcfs-control.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
mvn exec:java '-Dexec.args=--experiment STRICT_PRIORITY target/weighted-drf-strict-control.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
mvn exec:java '-Dexec.args=--experiment WMRF_LG target/weighted-drf-wmrf-control.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
mvn exec:java '-Dexec.args=--experiment WEIGHTED_DRF target/weighted-drf-replay.csv --seeds 42,43,44 --candidate experiments/service-bound-demo.properties'
Get-FileHash experiments/weighted-drf-results.csv, target/weighted-drf-replay.csv
git diff --check
```

Both Weighted DRF exports had SHA-256 `C885BF85B2D0234BD8A151E6A96533A9D151B145890F6AC069D0E05CA9B64B02`.
