# Milestone 2 verification and review handoff

Historical Milestone 2 report follows. For the current branch, see
[Milestone 3A verification](MILESTONE_3A_VERIFICATION.md) and the
[metric contract and readiness limitations](EXPERIMENT_METRICS.md). The current repository baseline
has 43 tests; the 53-test local dispatch framework mentioned in the new prompt is not committed
to any of the available remote branches. Historical branch/head statements below describe that
earlier session, not the current checkout.

Reverified locally on 19 November 2026. Branch: `feature/naveen-workloads`.
The session began on `main` and switched to the existing feature branch before making changes.
That branch already contained the Milestone 2 implementation in commit `6f8c218`, tracking
`origin/feature/naveen-workloads`. This verification preserved it and added two arrival regression
tests. No commit, push, PR creation or authorship change was performed in this session.
`main` remains at `05072633c806ff9eb076937aaab8f1e22cc83d8a`.

## Environment and commands actually executed

- Windows 11, OpenJDK 24.0.2 (`C:\Users\karpu\.jdks\openjdk-24.0.2+12-54`).
- Apache Maven 3.9.9, bundled with IntelliJ IDEA Community Edition 2025.2.
- Java compilation target 17; unchanged CloudSim Plus artifact 8.5.7 and JUnit Jupiter 5.11.4.
- Java and Maven were not on the session PATH. They were configured only in the command process;
  no global environment variables or installations were changed.
- Dependencies were downloaded into `$env:TEMP\group6-maven-cache`, outside this Git repository.
  Complete execution logs are under ignored `target/verification/`; JUnit XML reports are under
  `target/surefire-reports/`. These generated files are not part of the changes.

Commands (with the temporary Maven cache argument applied):

```powershell
$env:JAVA_HOME='C:\Users\karpu\.jdks\openjdk-24.0.2+12-54'
$env:Path="$env:JAVA_HOME\bin;C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2\plugins\maven\lib\maven3\bin;$env:Path"
mvn "-Dmaven.repo.local=$env:TEMP\group6-maven-cache" clean test
mvn "-Dmaven.repo.local=$env:TEMP\group6-maven-cache" exec:java
mvn "-Dmaven.repo.local=$env:TEMP\group6-maven-cache" exec:java '-Dexec.args=BATCH_HEAVY'
mvn "-Dmaven.repo.local=$env:TEMP\group6-maven-cache" exec:java '-Dexec.args=INTERACTIVE_HEAVY'
mvn "-Dmaven.repo.local=$env:TEMP\group6-maven-cache" exec:java '-Dexec.args=INFERENCE_HEAVY'
mvn "-Dmaven.repo.local=$env:TEMP\group6-maven-cache" exec:java '-Dexec.args=--smoke'
mvn "-Dmaven.repo.local=$env:TEMP\group6-maven-cache" exec:java '-Dexec.args=BALANCED 7 99 100 1'
mvn "-Dmaven.repo.local=$env:TEMP\group6-maven-cache" dependency:sources '-DincludeGroupIds=org.cloudsimplus'
git diff --check
git status --short --branch
```

The `dependency:sources` command fetched the published 8.5.7 source artifact for API inspection, including the
broker and Cloudlet interfaces whose Javadoc pages could not be fetched by the browsing tool.
On a normal installation, use the standard commands in the README without the cache override.

## Test results

The existing feature-branch baseline passed **41 tests, 0 failures, 0 errors, 0 skipped**.
After adding the two arrival regression cases, `mvn clean test` passed:

| Test class | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| SimulationConfigTest | 3 | 0 | 0 | 0 |
| SimulationControllerTest | 1 | 0 | 0 | 0 |
| WorkloadSimulationTest | 9 | 0 | 0 | 0 |
| DatacenterFactoryTest | 2 | 0 | 0 | 0 |
| RamModelTest | 2 | 0 | 0 | 0 |
| WorkloadGeneratorTest | 21 | 0 | 0 | 0 |
| WorkloadValidatorTest | 5 | 0 | 0 | 0 |
| **Total** | **43** | **0** | **0** | **0** |

The original simulation test retains its completion, metadata, broker, topology and overlap checks;
its RAM assertion was updated to assert absolute MB and unit instead of the removed fraction field.
The two original datacenter tests are unchanged.

Parameterized tests cover all scenarios. Additional loops check five seeds and eight counts per
scenario, including empty traces, small rounding cases and non-multiples of 100. Tests also cover
independent random streams, rate scaling, exponential mean/variance for a fixed 10,000-task sample,
custom resource profiles, defensive copies, invalid inputs/imported traces, impossible host packing,
single-task VM limits, exact CloudSim arrivals, contention, trace reuse and real RAM accounting.
The new regression tests execute tasks across idle gaps at 0, 100 and 1,000 seconds, and simultaneous
mixed-class arrivals with non-sequential IDs. They assert exact arrivals/idle starts, completion,
metadata identity by ID and total Cloudlet work equal to per-PE length times PE count.
The RAM-oversubscription test intentionally terminates its observation simulation early; it is
testing simultaneous admission, not completion. All full workload simulations finish successfully.

## Actual default simulation output (selected lines)

```text
Scenario: BALANCED
Seed: 42
Total tasks: 120
Arrival horizon: 600.000 s; rate: 0.500 tasks/s
Task arrival range: 1.288626..223.034043 s after workload epoch
BATCH: 48
  Length: 30546..115154 MI/PE; PEs: 1..2; RAM: 1052..2010 MB
INTERACTIVE: 42
  Length: 2005..9883 MI/PE; PEs: 1..1; RAM: 263..1017 MB
AI_INFERENCE: 30
  Length: 10744..46537 MI/PE; PEs: 1..2; RAM: 1630..2986 MB
Same-seed trace reproduction: PASS
Workload epoch (simulation seconds): 0.100000
Maximum datacenter-arrival error (seconds): 0.000000000
Completed cloudlets (120)
[INFO] BUILD SUCCESS
```

The CLI prints all completed Cloudlet rows as well. These are actual observed ranges, not the
configured theoretical bounds. Arrival times are relative to the measured workload epoch.

| Executed CLI scenario | Batch / Interactive / Inference | Successful Cloudlets | Epoch (s) | Max arrival error (s, printed) | Result |
| --- | --- | ---: | ---: | ---: | --- |
| BALANCED | 48 / 42 / 30 | 120 | 0.100000 | 0.000000000 | BUILD SUCCESS |
| BATCH_HEAVY | 72 / 24 / 24 | 120 | 0.100000 | 0.000000000 | BUILD SUCCESS |
| INTERACTIVE_HEAVY | 24 / 72 / 24 | 120 | 0.100000 | 0.000000000 | BUILD SUCCESS |
| INFERENCE_HEAVY | 24 / 24 / 72 | 120 | 0.100000 | 0.000000000 | BUILD SUCCESS |
| Milestone 1 (`--smoke`) | 1 / 1 / 1 | 3 | 0.100000 | 0.000000000 | BUILD SUCCESS |

All four generated CLI runs print same-seed reproduction PASS. Their arrival ranges are identical,
as intended by the independent arrival stream, even though their class assignments differ.
A custom CLI run, `BALANCED 7 99 100 1`, also passed: seed 99, seven completed tasks with counts
3 / 2 / 2, horizon 100 seconds, rate 1 task/second, same-seed reproduction PASS and printed arrival
error 0.000000000 seconds. This exercises every positional configuration argument.

## Files and ownership

The complete feature branch adds the following files relative to `main`; these modules already
existed on the branch when this session began. In `src/main/java/com/group6/cloudsim/workload/`:
`WorkloadProfile.java`, `WorkloadScenario.java`, `WorkloadGenerationConfig.java`,
`WorkloadGenerator.java`, `WorkloadTrace.java`, `WorkloadValidator.java`, `RamAdmissionLedger.java`.

Created tests: `core/SimulationConfigTest.java`, `core/WorkloadSimulationTest.java`,
`workload/WorkloadGeneratorTest.java`, `workload/WorkloadValidatorTest.java`, `workload/RamModelTest.java`.
Created documentation: this file and `docs/WORKLOAD_MODEL.md`.

Modified: `README.md`, `Main.java`, `core/SimulationConfig.java`, `core/SimulationController.java`,
`datacenter/DatacenterFactory.java`, `workload/CloudletFactory.java`, `workload/TaskMetadata.java`,
and the RAM assertion in `core/SimulationControllerTest.java`.

Changes to shared orchestration/configuration are limited to consuming the new workload/infrastructure
inputs and exposing the measured time origin. No other member's proposed/baseline scheduling or
evaluation modules were implemented.

This session created no new source files. It modified `core/WorkloadSimulationTest.java`,
`README.md`, `docs/WORKLOAD_MODEL.md` and this verification document. The application implementation
required no further changes after inspection and execution tests.

## Git state and pull request preparation

Working branch: `feature/naveen-workloads`, tracking `origin/feature/naveen-workloads` at `6f8c218`.
There are four modified tracked files, all unstaged and uncommitted. The pre-existing untracked
`user.name/` directory was left unchanged and is excluded from the PR commands below.
`git diff --check` passed; main and commit history were untouched.

When the developer explicitly approves committing/pushing, review and run:

```powershell
git switch feature/naveen-workloads
git diff --check
git diff
git status --short
git add README.md docs/WORKLOAD_MODEL.md docs/MILESTONE_2_VERIFICATION.md src/test/java/com/group6/cloudsim/core/WorkloadSimulationTest.java
git diff --cached
git commit -m "Verify workload arrivals across idle gaps and simultaneous submissions"
git push -u origin feature/naveen-workloads
```

Use the contributor's own configured identity; do not override authorship or assign contributions
to people who did not perform them. In the private repository, create a PR from
`feature/naveen-workloads` into `main`, request Akshith's review, and include:

**Suggested title:** Add reproducible mixed workloads and validated research infrastructure

**Suggested description:**

> Adds immutable seeded batch/interactive/inference traces with exact scenario counts and
> configurable resource profiles, arrival rates and horizons. Connects per-task delays to a measured
> VM-ready epoch and validates the three-host/six-VM topology. RAM is modeled in absolute MB;
> concurrent task reservations remain a documented later integration through RamAdmissionLedger.
>
> Validation: 43 JUnit tests pass with `mvn clean test`; all four 120-task scenarios and the preserved
> three-task smoke scenario pass `mvn exec:java`. Same-seed records reproduce exactly and printed
> maximum datacenter-arrival error is 0.000000000 s. See docs/WORKLOAD_MODEL.md and
> docs/MILESTONE_2_VERIFICATION.md for modeling details and actual output.

## Remaining limitations

No unresolved build/test failures. JDK 25.0.1/Maven 3.10.1 were not available locally; verification
used the installed versions listed above. The source still compiles with release 17.
Task-level concurrent RAM reservation is intentionally deferred, response targets are metadata,
and trace timestamps use a documented workload epoch rather than absolute simulator zero.
The finite horizon may reject a sampled workload; this is explicit validation, not lost tasks.
Future VM boot/network-delay changes require revalidating arrival integration.
