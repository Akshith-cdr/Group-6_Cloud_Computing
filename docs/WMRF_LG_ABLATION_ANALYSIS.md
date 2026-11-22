# WMRF-LG latency guard and ageing ablation

All four variants ran on the same 160 traces using the same dispatcher, entitlement
weights, accounting window and topology. The full variant reproduced standard WMRF-LG
on every measured row. Every variant completed 19,200 tasks across 160 runs. The paired
differences below use **variant minus Full**, so positive values mean longer response
or waiting time. Each contrast uses 20 seeds. Brackets give exploratory pointwise 95%
bootstrap intervals. See the [ablation summary](../experiments/final/wmrf-lg-ablation-summary.csv)
and [paired contrasts](../experiments/final/wmrf-lg-ablation-contrasts.csv).

## Component-removal effects

| Configuration | Scenario | No latency guard, Interactive mean response Δ (s) | No latency guard, Inference mean response Δ (s) | No ageing, Batch mean queue wait Δ (s) |
| --- | --- | ---: | ---: | ---: |
| Original stress | Balanced | +0.230 [−0.221, +0.694] | +1.220 [−6.044, +8.072] | 0.000 [0.000, 0.000] |
| Original stress | Batch-heavy | +0.117 [−0.324, +0.517] | +1.979 [−4.346, +9.900] | +0.137 [−0.966, +1.172] |
| Original stress | Interactive-heavy | −0.234 [−0.734, +0.240] | −0.848 [−4.435, +2.294] | +0.122 [−0.184, +0.477] |
| Original stress | Inference-heavy | +0.084 [−0.204, +0.383] | +1.270 [−1.112, +4.609] | +0.419 [−0.839, +2.085] |
| Unapproved candidate | Balanced | +0.130 [−0.067, +0.402] | +2.293 [−0.869, +6.451] | −0.266 [−0.895, +0.249] |
| Unapproved candidate | Batch-heavy | +0.001 [0.000, +0.001] | −0.002 [−0.006, 0.000] | +0.269 [−0.685, +1.363] |
| Unapproved candidate | Interactive-heavy | +0.042 [−0.087, +0.241] | +0.036 [−0.317, +0.403] | −0.122 [−0.298, +0.008] |
| Unapproved candidate | Inference-heavy | +0.005 [−0.097, +0.131] | +2.740 [+0.904, +5.131] | +0.472 [0.000, +1.196] |

Source for every table cell is the [paired contrasts CSV](../experiments/final/wmrf-lg-ablation-contrasts.csv).
Intervals and means are rounded to milliseconds here.

- **Interactive response.** Removing the guard produced small, mixed changes, from
  −0.234 s to +0.230 s across strata. Balanced stress was slightly slower without it,
  while Interactive-heavy stress was slightly faster. Both intervals span zero. The
  results show no consistent Interactive benefit from the guard on these traces.
  See [Interactive ablation figure](../experiments/final/plots/ablation-interactive-mean-response.png).
- **Inference response.** Removing the guard increased mean inference response by
  2.740 s in the Inference-heavy candidate. All four original-stress inference groups
  were intrinsically infeasible. The guard gives those tasks zero direct urgency, so
  their stress differences reflect dispatch changes involving other tasks. Balanced
  candidate inference increased by 2.293 s, with a wide interval spanning zero.
  See [Inference ablation figure](../experiments/final/plots/ablation-inference-mean-response.png)
  and [paired contrasts](../experiments/final/wmrf-lg-ablation-contrasts.csv).
- **Batch waiting.** Removing ageing lengthened mean wait by 0.419 s in Inference-heavy
  stress and 0.472 s in the corresponding candidate. Balanced stress was identical for
  this metric, and Balanced and Interactive-heavy candidate mean waits fell when ageing
  was removed. These finite workloads do not show a uniform Batch protection benefit
  from ageing alone. See [Batch wait figure](../experiments/final/plots/ablation-batch-mean-queue-wait.png)
  and [paired contrasts](../experiments/final/wmrf-lg-ablation-contrasts.csv).

## Joint effect and interaction

Across strata, removing both components changed mean full-run Jain by at most
**0.0000198** in absolute value. Balanced stress changed by −0.00000309
(95% interval −0.00002105 to +0.00001437), while the Balanced candidate changed by
+0.00001972 (0.00000705 to 0.00003500). The observed magnitudes are small relative
to workload-mix differences. The index summarizes full-run service and has limited
sensitivity to changes in dispatch order on drained traces. These intervals are
pointwise and do not support a multiplicity-adjusted significance claim.
See [paired contrasts](../experiments/final/wmrf-lg-ablation-contrasts.csv)
and the [Jain ablation figure](../experiments/final/plots/ablation-jain-fairness.png).

The factorial interaction is the combined removal effect minus the two individual
removal effects, matched within each seed. For Balanced candidate inference response,
removing both raised the mean by 2.315 s, compared with individual increases of 2.293 s
and 2.799 s. The interaction was −2.777 s (−6.852 to +0.395). For Inference-heavy
candidate inference response it was +0.851 s (−0.055 to +2.454), and for Balanced
stress Interactive response it was −0.023 s (−0.066 to +0.0003). These estimates
show non-additivity in the observed schedules, with uncertainty and no consistent
direction across strata. The inference-heavy candidate showed a clearer latency-guard
effect than the corresponding stress stratum. The candidate changes work demands and
which tasks receive urgency, so each configuration needs its own interpretation.
See the [paired contrasts](../experiments/final/wmrf-lg-ablation-contrasts.csv)
and [methodology](FINAL_EXPERIMENT_METHODOLOGY.md).
