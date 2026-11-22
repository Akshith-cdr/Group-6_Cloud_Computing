# Final four-policy comparative analysis

The analysis uses 20 seeds per scenario and configuration, with 160 matched immutable traces and
640 completed comparative runs. Each policy completed all 19,200 tasks it received. Differences
are WMRF-LG minus baseline on the same trace. Intervals are pointwise, exploratory 95% paired
bootstrap intervals. See the [summary](../experiments/final/comparative-summary.csv),
[paired differences](../experiments/final/paired-comparisons.csv), and
[methodology](FINAL_EXPERIMENT_METHODOLOGY.md).

## Latency and waiting

These Balanced original-stress figures are means of each run's statistics, in seconds.

| Policy | Interactive mean response | Interactive mean wait | Interactive P95 / P99 | Inference mean response | Inference mean wait | Inference P95 / P99 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| FCFS | 77.06 | 71.07 | 145.94 / 154.37 | 176.15 | 146.09 | 347.49 / 362.01 |
| Strict Priority | 9.44 | 3.45 | 17.49 / 20.94 | 89.34 | 59.28 | 185.84 / 198.64 |
| Weighted DRF | 10.27 | 4.29 | 19.73 / 23.12 | 90.53 | 60.48 | 182.34 / 191.36 |
| WMRF-LG | 9.78 | 3.80 | 20.50 / 25.01 | 100.18 | 70.13 | 205.39 / 214.82 |

WMRF-LG's paired mean Interactive response difference in this stratum was **−67.27 s**
against FCFS (95% interval −80.48 to −54.47), **+0.34 s** against Strict Priority (−0.09 to
+0.83), and **−0.49 s** against Weighted DRF (−1.08 to +0.07). Strict Priority had the lowest
mean Interactive response across all eight measured scenario/configuration strata. The
ordering varies by class and metric. In Batch-heavy stress, WMRF-LG had lower mean inference
P95/P99 (195.81/211.36 s) than Weighted DRF (208.15/223.85 s), while in inference-heavy
stress WMRF-LG had the highest mean inference response (171.72 s) and Strict Priority the
lowest (101.40 s). The corresponding paired difference was +70.33 s (63.20 to 77.52).
See the [summary](../experiments/final/comparative-summary.csv) and
[paired differences](../experiments/final/paired-comparisons.csv).

## SLA context

Every original-stress inference task was intrinsically infeasible in this sample, so its
feasible-only miss ratio is unavailable. In Balanced stress, the average Interactive
feasible-only miss rates were **84.5% FCFS, 54.8% Strict Priority, 61.9% Weighted DRF, and
47.6% WMRF-LG**. WMRF-LG minus FCFS was −36.9 percentage points (−43.0 to −31.3). These are
means of per-run ratios. The total SLA miss rate includes the CPU-bound unavoidable misses.
See the [summary](../experiments/final/comparative-summary.csv) and
[paired differences](../experiments/final/paired-comparisons.csv).

The unapproved demonstration candidate changes MI/PE ranges, and all its SLA-bearing
tasks are intrinsically feasible. Inference has a 10 s target and exactly 10 s ideal
CPU time, leaving zero ideal slack for queueing or simulated execution overhead.
Mean inference miss rate was about 99.2% for each policy in the Balanced candidate.
Mean inference response nonetheless ranged from 36.33 s (Weighted DRF) to 115.89 s
(FCFS) in that stratum. Keep this candidate separate from the approved stress workload
and read latency alongside the near-saturated miss rate.
See the [summary](../experiments/final/comparative-summary.csv) and
[feasible-SLA figure](../experiments/final/plots/intrinsically-feasible-sla-miss-ratio.png).

## Throughput and capacity occupancy

FCFS had the highest mean throughput in seven of eight strata. In inference-heavy stress,
WMRF-LG led at **0.219 tasks/s**, close to Weighted DRF at **0.218**, FCFS at **0.218**,
and Strict Priority at **0.210**. In Interactive-heavy stress the paired WMRF-LG minus
FCFS difference was −0.00654 tasks/s (95% interval −0.010 to −0.003 after rounding).
In Balanced stress, FCFS and WMRF-LG reserved about 90.3% and 89.1% of VM PE-time,
respectively, and 49.3% and 48.7% of VM RAM-time. These are time-weighted reservations over
each policy's complete-run interval. A shorter time to finish can increase the measured
occupancy and throughput even as another policy protects a class's latency. Physical CPU
and RAM utilization were outside the instrumentation.
See the [summary](../experiments/final/comparative-summary.csv) and
[paired differences](../experiments/final/paired-comparisons.csv).

## Batch protection and Jain fairness

| Inference-heavy original stress | Mean Batch wait (s) | Mean of per-run maximum Batch wait (s) | Mean Batch makespan (s) | Unfinished Batch tasks |
| --- | ---: | ---: | ---: | ---: |
| FCFS | 97.79 | 254.56 | 535.93 | 0 |
| Strict Priority | 153.22 | 363.41 | 564.36 | 0 |
| Weighted DRF | 79.81 | 241.97 | 505.52 | 0 |
| WMRF-LG | 67.43 | 210.29 | 469.84 | 0 |

Here WMRF-LG reduced average run-maximum Batch wait by 153.12 s relative to Strict Priority
(paired interval −172.45 to −133.66), while Strict Priority protected inference response.
That Batch pattern varies. In Balanced stress, WMRF-LG's mean Batch makespan was
636.49 s compared with 627.98 s for FCFS. The controlled Strict Priority regression
shows 8.770 s of repeated Batch deferral across eight Interactive completions. Every
finite trace in the sweep drained, so these observations quantify waiting rather than
permanent starvation. See the [summary](../experiments/final/comparative-summary.csv),
[paired differences](../experiments/final/paired-comparisons.csv) and
[starvation test](MILESTONE_3A_VERIFICATION.md#strict-priority-baseline-verification).

Full-run Jain differentiated workload mixes more than scheduling orders. The largest
absolute mean paired WMRF-LG versus baseline difference across strata was only
0.0000363. For example, original-stress Jain averaged roughly 0.424 in the Batch-heavy mix
and 0.695 in the Inference-heavy mix for all four. The index uses full-run
normalized dominant reservation service relative to entitlements. When all tasks drain,
similar per-class work and reservation demands can yield similar Jain values despite
very different queueing experiences. See the [paired differences](../experiments/final/paired-comparisons.csv)
and [metric contract](EXPERIMENT_METRICS.md).

WMRF-LG's strongest observed advantages were Interactive latency and feasible Interactive
SLA outcomes against FCFS, and Batch protection against Strict Priority in inference-heavy
stress. Strict Priority favored Interactive response, often at Batch's expense, and FCFS
often finished the overall trace faster. Weighted DRF was competitive in several strata,
including mean Inference response on the Balanced demonstration candidate. These
trade-offs describe the modeled topology, workload draws and finite arrival horizon.
The intervals are descriptive, with no multiplicity-adjusted significance claims.
See the [complete comparison](../experiments/final/paired-comparisons.csv).
