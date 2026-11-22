# Final experiment audit

## Repository and evidence

Started on `feature/akshith-wmrf-lg` at PR #4 merge `3585428`. All four later policy
implementations and their CSVs were present as uncommitted work. Inspected tracked and
untracked changes, the Java/Maven setup, policy and dispatcher sources, trace generation,
feasibility, metric collection, fairness, experiment routing and CSV formatting, plus the
four design documents and prior verification reports. CloudSim Plus remains 8.5.7 and
the Maven compiler target remains Java 17. Reviewed the installed broker/scheduler APIs
and used the actual terminal-release and arrival timing tests for behavioral evidence.

## Dispatch and policy review

| Area | Verified behavior |
| --- | --- |
| Shared lifecycle | The broker enqueues only due workload arrivals and submits a fresh Cloudlet after atomic PE/RAM reservation. Capacity snapshots refresh on each admission. Via CloudSim's completion return or dispatched scheduler failure/cancellation, releases wake waiting work. The same VM factory and placement constraints apply to every policy. |
| FCFS | Compares workload arrival and task ID independently of class. A blocked candidate stays queued, and the lowest-ID feasible VM is selected. |
| Strict Priority | Interactive > AI inference > Batch, using FCFS within class and feasible-task bypass. The controlled prior test deferred a Batch task across eight Interactive completions for 8.770 seconds. The Batch queue-wait and unfinished fields expose separate outcomes in finite sweeps. |
| Weighted DRF | Uses active PE and RAM reservations from `VmReservations`, divided by participating VM capacity. The dominant share is the larger of the two fractions, divided by class entitlement. Each accepted reservation affects the next decision. Shares tie by class identifier, with workload arrival and task ID within class. It uses current allocation and no urgency or ageing. |
| WMRF-LG | The rolling accountant sums the overlap of released plus active reservation intervals with the observation window. The denominators are total VM PEs and RAM times the elapsed window. Service uses the larger normalized share divided by entitlement. Urgency is bounded and is zero for absent or intrinsically infeasible SLAs. Ageing is bounded and follows simulated wait. The existing score and work-conserving borrowing were preserved. |

The trace and profile records are immutable. The generator and demonstration calibration
run before policy selection. Calibration shares the initial trace and preserves sampled
arrivals, class labels, PEs and RAM while changing the designated MI/PE ranges. The approved
stress targets and machine configuration remain intact.

## Metrics

- Workload arrival is workload epoch plus trace arrival. Ready entry and dispatch are broker
  events. API submission is at dispatch on the shared path, and actual execution start and
  successful finish come from CloudSim. Response = completion − arrival, queue wait =
  dispatch − arrival, pre-execution delay = start − dispatch, and service = completion − start.
  Response, mean/max wait and percentile populations are completed tasks. Missing timestamps
  and empty cohorts remain unavailable. P95/P99 use nearest rank.
- SLA feasibility checks MI/PE divided by eligible VM MIPS/PE. Batch is excluded. Actual misses
  compare completed finish against the absolute workload deadline. The total ratio denominator
  is all SLA-bearing tasks. The feasible ratio denominator is solely the intrinsically feasible
  subset. Failed, cancelled and unfinished SLA tasks are unresolved. CPU-bound unavoidable
  misses are reported as such rather than attributed to dispatch. All stress inference tasks in
  this sample were intrinsically infeasible. Candidate inference uses the 10 s CPU bound with
  zero ideal queueing slack.
- The common interval for throughput and occupancy starts at VM readiness. For these completed
  runs it ends at the final successful completion, excluding shutdown grace. The class numerator
  is its completed count and the aggregate numerator is the sum. The CSV validator confirms
  rows share that denominator. CPU and RAM are reservation occupancy fractions, including
  elapsed idle time. Physical utilization and measured execution load are unavailable.
- Jain uses `x_c = max(PE occupancy, RAM occupancy)/weight_c` and
  `(sum x_c)^2/(n × sum x_c^2)`. An arrived class with zero service is included. A class with
  no arrival by the cutoff is excluded. All-zero service, missing shares or invalid weights
  make the index unavailable. It measures full-run normalized exposure, which can be
  almost unchanged even when task order and latency differ. Demand and reservations across
  the drained workload influence it.

## CSV audit and corrections

Each historical file contained 96 rows, representing 24 distinct runs with one aggregate
and three class rows. FCFS and WMRF-LG used the earlier header without maximum queue wait.
Strict Priority and Weighted DRF included that field. The validator checked all expected keys,
policy IDs, purposes, task totals, SLA subcohorts and normalized metrics. For seeds 42–44,
every historical field in all 96 runs reproduced exactly in the final sweep. The historical
files kept their original SHA-256 hashes:

| File | SHA-256 |
| --- | --- |
| fcfs-results.csv | `94cf05b47a892900ac7a6d6acab0d32d6923fee4c611918f7c11e6ee43f2864f` |
| strict-priority-results.csv | `fd6cff9077a1bda445ae4bcb379e2089d0b5378b4530e3838660affd170349f5` |
| weighted-drf-results.csv | `c885bf85b2d0234bd8a151e6a96533a9d151b145890f6ac069d0e05ca9b64b02` |
| wmrf-lg-results.csv | `eebe6a0a048111590c5001e15366eb49dcb65bf1f32b34859b79cb80f9731d34` |

Historical exports contained no trace fingerprints, so historical payload identity cannot
be read directly from those files. The final command fixes this evidence gap by reusing
each actual immutable trace within all eight runs and recording its canonical SHA-256
plus a per-run identifier and manifest. All 160 fingerprints match across corresponding
runs, and each is unique across the 160 trace keys. The analysis rejects data with
mismatched controls or non-success statuses. No policy formula or metric denominator
required correction for these runs. The mathematical and inferential limits of these
experiments are recorded alongside the [methodology](FINAL_EXPERIMENT_METHODOLOGY.md).

## Executed verification

- The clean Maven suite passed 128 tests with zero failures, errors or skips. The focused
  policy, dispatcher, reservation and final-experiment suite passed 60 tests.
- The stock smoke run completed all three Cloudlets with zero datacenter-arrival error.
- Independent seed-42 replay reproduced 32 comparative and 32 ablation runs, including
  every policy and variant, with exact CSV field equality against the extended sweep.
- All 1,280 manifest rows report success, with 153,600 completions and zero failed,
  cancelled or unfinished tasks. Full ablation reproduced all standard WMRF-LG metrics
  and observation windows. Analysis reconciled all class outcome counts and occupancies.
- All four historical hashes were preserved and `git diff --check` passed.

See the [run manifest](../experiments/final/run-manifest.csv) and
[reproduction commands](FINAL_EXPERIMENT_METHODOLOGY.md#reproduction).
