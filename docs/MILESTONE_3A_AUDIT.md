# Independent Milestone 3A audit

## Repository and scope

The worktree was clean on `main` at `3585428`, matching the local `origin/main` reference. That
merge includes Aswarth's PR #4, with evaluation implementation `b147cf1` and follow-up fixes
`be3ca18`. The requested `feature/akshith-wmrf-lg` branch was created at the merge.
The first-parent diff modifies `.gitignore`, `Main`, `SimulationController`, README and existing
workload, contribution and milestone documents. It adds calibration, feasibility, observation,
metrics, runner/reporting, the evaluation tests and an illustrative candidate. To reproduce the
inventory, run `git diff --name-status '3585428^1' 3585428`.

Reviewed the project structure and POM, README, contribution plan, workload model, metric contract
and Milestone 2/3A verification. Inspected the evaluation classes and tests, workload configuration,
validator, immutable trace and task metadata, workload/Cloudlet factories, stock controller and
broker, topology and integration tests. `DispatchBroker`, `DispatchPolicy`,
`DispatchCloudletScheduler`, `VmReservations`, `FairnessCalculator`, `CsvExporter` and a
chart generator were absent in the merge. The milestone adds the dispatcher, reservations,
fairness calculation and CSV support. There is no chart generator.

## Findings and corrections

| Area | Finding and action |
| --- | --- |
| SLA lower bound | Installed CloudSim Plus 8.5.7 bytecode confirms per-PE Cloudlet work and progression at per-PE MIPS. Requested PEs affect placement, total work and total capacity equally. Retained the analyzer's fastest PE/RAM-eligible VM calculation and equality rule. A task with an SLA and no eligible VM has an infinite mathematical bound. The simulation config rejects that placement earlier. |
| SLA outcomes | Batch has no SLA. Existing ratios divide measured completion misses by all SLA-bearing inputs, or by the intrinsically feasible input subset. Failed, cancelled and unfinished tasks are reported as unresolved. For incomplete runs these ratios are partial observed miss fractions. A completed task finishing at an exact deadline could be recorded as a miss from floating-point subtraction. Fixed the comparison to use the absolute deadline and added a one-ULP regression. |
| Clocks | Stock submission is measured at VM readiness and CloudSim schedules future datacenter arrivals. The stock path could not measure ready/dispatch times, so its queue metrics remain absent. The new broker records ready and dispatch events on the workload clock and keeps execution start and successful finish from CloudSim. |
| Latency and throughput | Verified outcome separation, completed-only response statistics, even/odd median and nearest-rank P95/P99. All class throughputs share the epoch-to-end interval. Added aggregate statistics over the pooled tasks and batch makespan for fully completed batch cohorts. |
| CPU/RAM | Stock space sharing checks CPU and accepts concurrent RAM demand above VM capacity. CloudSim utilization samples alone cannot give a verified time average. Added hard paired reservations and occupancy integrals over actual simulated intervals for the dispatched path. |
| Failure and cancellation | In the installed 8.5.7 scheduler, the stock stop path removes a running Cloudlet from the execution list without decrementing used PEs. The dispatched scheduler uses CloudSim's protected PE-aware removal method, restores broker expected PEs, and releases both reservations on terminal stop. Tests inject real CloudSim fail/cancel events and verify the full-size waiting Cloudlet starts immediately. |
| Fairness | Milestone 3A reported Jain as unavailable. Added Jain over dominant reservation capacity-time divided by class entitlement, with zero-service arrived classes included, empty/future-only classes excluded and undefined denominators withheld. |
| Reproducibility | The trace/metadata/configuration are immutable. Repeated WMRF-LG and stock runs share the trace and original arrivals, create fresh Cloudlets and preserve physical VM placement. Calibration keeps exact realized IDs, arrival times, class, PEs and RAM while changing only named ranges/targets. |

Installed API inspection used `javap -c -p` against the Maven `cloudsimplus-8.5.7.jar` for
`CloudletExecution`, `CloudletSchedulerAbstract`, `DatacenterBrokerAbstract` and
`DatacenterSimple`. The stock and dispatched timing tests exercise those methods in simulation.
See the [CloudSim Plus 8.5.7 broker reference](https://javadoc.io/static/org.cloudsimplus/cloudsimplus/8.5.7/org/cloudsimplus/brokers/DatacenterBrokerAbstract.html).

## Independent checks and readiness

All 66 tests in the untouched merge passed before implementation. Final checks, commands and
result coverage are recorded in [Milestone 3A verification](MILESTONE_3A_VERIFICATION.md).
The metric framework supports descriptive evaluation of WMRF-LG on the supplied traces.
The measured utilization is reservation occupancy. The finite workload, fixed homogeneous
topology, CPU lower-bound assumptions, incomplete-run SLA semantics and demand-sensitive Jain
index bound interpretation. The named candidate is mathematically feasible and unapproved.
Missing baseline policies and the stock broker's different admission semantics preclude final
comparative conclusions.
