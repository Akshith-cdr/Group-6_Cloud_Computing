# Group 6 contribution plan

IIIT Sri City · Cloud Computing · Topic 22

The percentages below are planning targets for the full project. They guide workload allocation. Actual contributions will be tracked through individual commits, pull requests, and code reviews, and the targets carry no assigned marks.

| Member | Target | Recommended branch | Primary ownership |
| --- | ---: | --- | --- |
| Akshith Veerisetty | 36% | `feature/akshith-wmrf-lg` | Architecture, application entry point, configuration, simulation lifecycle, WMRF-LG, and integration |
| Sajja Venkata Naveen | 32% | `feature/naveen-workloads` | Datacenter, hosts, VMs, workload metadata and generation, and VM placement |
| Pitti Aswarth Narayana | 32% | `feature/aswarth-evaluation` | Scheduling baselines, performance metrics and fairness, experimental execution, and result reporting |

## Module ownership

| Member | Milestone 1 maintenance | Planned modules |
| --- | --- | --- |
| Akshith | `Main.java`, `SimulationConfig.java`, `SimulationController.java`, and integration checks | `DispatchBroker.java`, `DispatchPolicy.java`, `VmReservations.java`, `ResourceTimeAccountant.java`, `WmrfLgPolicy.java` |
| Naveen | `DatacenterFactory.java`, `CloudletFactory.java`, `WorkloadClass.java`, `TaskMetadata.java`, and infrastructure tests | `HostFactory.java`, `VmFactory.java`, `WorkloadGenerator.java`, `VmPlacementPolicy.java` |
| Aswarth | Support review of completion output and test cases that establish repeatability | `FcfsScheduler.java`, `PriorityScheduler.java`, `WeightedDrfBaseline.java`, `MetricsCollector.java`, `FairnessCalculator.java`, `ExperimentRunner.java`, `CsvExporter.java`, `ChartGenerator.java` |

Akshith coordinates integration and validates the full simulation. Naveen documents infrastructure and workload inputs so the team can reproduce the same scenarios. Aswarth defines metrics and validation cases, keeps policy comparisons on identical inputs, and verifies reported results. The ownership above assigns responsibility for future work and maintenance.

## GitHub workflow

Milestone 3A uses `feature/aswarth-m3a-evaluation` for Aswarth's feasibility analyzer, metric contract,
calibration, experiment-runner preparation, tests and reporting. The merged PR #4 is the evaluation
foundation. The final core milestone adds an independent audit, arrival-driven scheduling,
reservation metrics and reproducible WMRF-LG experiments. Approved
stress profiles and topology are shared with earlier milestones. FCFS, Strict Priority and Weighted
DRF use the same dispatcher, resource reservations and metrics as WMRF-LG. The illustrative
calibration candidate is unapproved. See the [FCFS design](FCFS_DESIGN.md),
[Strict Priority design](STRICT_PRIORITY_DESIGN.md), [Weighted DRF design](WEIGHTED_DRF_DESIGN.md)
and [verification](MILESTONE_3A_VERIFICATION.md).

1. Keep the repository private. Each member uses their own GitHub account and Git identity.
2. Start a focused branch from current `main`. Use the recommended branches for the initial workstreams.
3. Implement and test meaningful changes independently. Each member makes and pushes their own commits under their own identity. Commit and review activity should reflect the work each person actually performed.
4. Open a pull request against `main` in the private repository. Describe the change, affected modules, and verification results. For simulation changes, include the scenario inputs needed to reproduce the run.
5. Request review from Akshith and any affected module owner. Another team member reviews Akshith's implementation changes. Resolve feedback in the pull request so decisions stay traceable.
6. Akshith coordinates merges after review and checks `mvn clean test` and `mvn exec:java` on the integrated branch. Keep the original commit identities visible in the project history.
