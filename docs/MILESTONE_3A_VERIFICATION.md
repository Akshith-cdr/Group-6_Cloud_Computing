# Milestone 3A verification and integration handoff

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
