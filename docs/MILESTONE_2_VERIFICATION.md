# Milestone 2 verification and review handoff

Verified locally on 19 November 2026. Branch: `feature/naveen-workloads`.
At the initial verification checkpoint, no commit, push or PR creation had been performed.
Git identity and the main branch were unchanged. Subsequent publication is recorded by Git history
and the pull request.

## Environment and commands actually executed

- Windows 11, OpenJDK 24.0.2 (`C:\Users\karpu\.jdks\openjdk-24.0.2+12-54`).
- Apache Maven 3.9.9, bundled with IntelliJ IDEA Community Edition 2025.2.
- Java compilation target 17; unchanged CloudSim Plus artifact 8.5.7 and JUnit Jupiter 5.11.4.
- Java and Maven were not on the session PATH. They were configured only in the command process;
  no global environment variables or installations were changed.
- Dependencies and execution logs were kept in the workspace's sibling `.tools` directory,
  outside this Git repository. That Maven cache is not part of the changes.

Commands (with the workspace-local Maven cache argument applied):

```powershell
$env:JAVA_HOME='C:\Users\karpu\.jdks\openjdk-24.0.2+12-54'
$env:Path="$env:JAVA_HOME\bin;C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2\plugins\maven\lib\maven3\bin;$env:Path"
# Optional cache override used for this managed workspace:
$env:MAVEN_OPTS='-Dmaven.repo.local="C:\Users\karpu\Downloads\CLOUD COMPUTING\.tools\m2"'
mvn clean test
mvn exec:java
mvn exec:java '-Dexec.args=BATCH_HEAVY'
mvn exec:java '-Dexec.args=INTERACTIVE_HEAVY'
mvn exec:java '-Dexec.args=INFERENCE_HEAVY'
mvn exec:java '-Dexec.args=--smoke'
mvn exec:java '-Dexec.args=BALANCED 7 99 100 1'
git diff --check
git status --short --branch
```

The actual tool invocations passed `-Dmaven.repo.local=...` directly to Maven; the example above
shows the equivalent process-local setup for convenient repetition. On a normal installation,
use the standard commands in the README without the cache override.

## Test results

The untouched Milestone 1 baseline first passed: **3 tests, 0 failures, 0 errors, 0 skipped**.
After implementation, `mvn clean test` passed:

| Test class | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| SimulationConfigTest | 3 | 0 | 0 | 0 |
| SimulationControllerTest | 1 | 0 | 0 | 0 |
| WorkloadSimulationTest | 7 | 0 | 0 | 0 |
| DatacenterFactoryTest | 2 | 0 | 0 | 0 |
| RamModelTest | 2 | 0 | 0 | 0 |
| WorkloadGeneratorTest | 21 | 0 | 0 | 0 |
| WorkloadValidatorTest | 5 | 0 | 0 | 0 |
| **Total** | **41** | **0** | **0** | **0** |

The original simulation test retains its completion, metadata, broker, topology and overlap checks;
its RAM assertion was updated to assert absolute MB and unit instead of the removed fraction field.
The two original datacenter tests are unchanged.

Parameterized tests cover all scenarios. Additional loops check five seeds and eight counts per
scenario, including empty traces, small rounding cases and non-multiples of 100. Tests also cover
independent random streams, rate scaling, exponential mean/variance for a fixed 10,000-task sample,
custom resource profiles, defensive copies, invalid inputs/imported traces, impossible host packing,
single-task VM limits, exact CloudSim arrivals, contention, trace reuse and real RAM accounting.
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

Created in `src/main/java/com/group6/cloudsim/workload/`:
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

## Git state and pull request preparation

Working branch: `feature/naveen-workloads`. At the initial verification checkpoint, the result was
8 modified tracked files and 14 new files (7 workload modules, 5 test classes, 2 documents),
all unstaged and uncommitted. `git diff --check` passed; main and commit history were untouched.

When the developer explicitly approves committing/pushing, review and run:

```powershell
git switch feature/naveen-workloads
git diff --check
git diff
git status --short
git add README.md docs/WORKLOAD_MODEL.md docs/MILESTONE_2_VERIFICATION.md src/main/java src/test/java
git diff --cached
git commit -m "Add reproducible mixed workload generation and research topology"
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
> Validation: 41 JUnit tests pass with `mvn clean test`; all four 120-task scenarios and the preserved
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
