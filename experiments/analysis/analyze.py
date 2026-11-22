"""Validate the paired simulation outputs and reproduce the research tables and figures."""
import argparse
import csv
import math
import re
from collections import defaultdict
from itertools import product
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

POLICIES = ("FCFS", "STRICT_PRIORITY", "WEIGHTED_DRF", "WMRF_LG")
VARIANTS = ("WMRF_LG_FULL", "WMRF_LG_NO_LATENCY_GUARD", "WMRF_LG_NO_AGEING",
            "WMRF_LG_NO_LATENCY_NO_AGEING")
SCENARIOS = ("BALANCED", "BATCH_HEAVY", "INTERACTIVE_HEAVY", "INFERENCE_HEAVY")
CONFIGS = ("original-stress", "service-bound-demo-unapproved")
CLASSES = ("ALL", "BATCH", "INTERACTIVE", "AI_INFERENCE")
SEEDS = tuple(range(42, 62))
COUNTS = ("completed", "failed", "cancelled", "unfinished", "sla_tasks", "intrinsically_feasible",
          "intrinsically_infeasible", "sla_misses", "feasible_sla_misses", "unresolved_sla")
METRICS = COUNTS + ("mean_response_s", "median_response_s", "p95_response_s", "p99_response_s",
                   "mean_queue_wait_s", "max_queue_wait_s", "throughput_per_s", "batch_makespan_s",
                   "cpu_reserved_fraction", "ram_reserved_fraction", "sla_miss_ratio",
                   "feasible_sla_miss_ratio", "jain_entitlement_normalized")
CONTROLS = ("purpose", "task_count", "arrival_horizon_s", "arrival_rate_per_s", "workload_profiles",
            "host_count", "host", "vm_count", "vm", "batch_weight", "interactive_weight",
            "inference_weight", "window_s", "measurement_start_s",
            "sla_tasks", "intrinsically_feasible", "intrinsically_infeasible", "trace_sha256")
PURPOSES = ("ORIGINAL_STRESS", "UNAPPROVED_COMPARISON_CANDIDATE")
MIXES = ((48, 42, 30), (72, 24, 24), (24, 72, 24), (24, 24, 72))
FILES = ("comparative-results.csv", "wmrf-lg-ablation-results.csv")
STAT_COLUMNS = ("n", "mean", "sd", "median", "min", "max", "ci95_lower", "ci95_upper")
BOOTSTRAP_SEED = 20261122
BOOTSTRAP_REPS = 10000


def require(condition, reason):
    if not condition:
        raise ValueError(reason)


def read_csv(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        rows = list(reader)
        require(reader.fieldnames and len(set(reader.fieldnames)) == len(reader.fieldnames)
                and all(None not in row and all(value is not None for value in row.values()) for row in rows),
                f"Invalid CSV header or row width: {path}")
        return reader.fieldnames, rows


def key(row):
    return row["scenario"], row["configuration"], int(row["seed"]), row["class"]


def indexed(path):
    fields, rows = read_csv(path)
    result = {(row["policy"], *key(row)): row for row in rows}
    require(len(result) == len(rows), f"Duplicate records: {path}")
    return fields, result


def metric(row, name):
    return float(row[name]) if row.get(name) else None


def approximately(actual, expected):
    return actual is not None and math.isclose(actual, expected, abs_tol=1e-8, rel_tol=1e-9)


def validate_rows(rows, policies, seeds, fingerprint=True):
    expected = set(product(policies, SCENARIOS, CONFIGS, seeds, CLASSES))
    require(set(rows) == expected, "Unexpected scenario/policy/configuration/seed/class coverage")
    for k, row in rows.items():
        policy, scenario, config, seed, workload_class = k
        metrics = {name: metric(row, name) for name in METRICS}
        require(all(value is None or (math.isfinite(value) and value >= -1e-8)
                    for value in metrics.values()), f"Invalid numeric metric: {k}")
        require(all(metrics[name] is not None and metrics[name].is_integer() for name in COUNTS),
                f"Invalid count: {k}")
        count = dict(zip(CLASSES, (120, *MIXES[SCENARIOS.index(scenario)])))[workload_class]
        require(int(row["task_count"]) == 120 and int(row["completed"]) == count
                and all(int(row[name]) == 0 for name in ("failed", "cancelled", "unfinished", "unresolved_sla")),
                f"Invalid task outcomes: {k}")
        require(row["purpose"] == PURPOSES[CONFIGS.index(config)], f"Wrong workload purpose: {k}")
        require(float(row["arrival_horizon_s"]) == 600 and float(row["arrival_rate_per_s"]) == 0.5
                and int(row["host_count"]) == 3 and int(row["vm_count"]) == 6
                and all(float(row[name]) == value for name, value in
                        zip(("batch_weight", "interactive_weight", "inference_weight", "window_s"),
                            (.30, .35, .35, 60))), f"Unexpected experiment controls: {k}")
        require(row["host"] == "MachineConfig[pes=4, mipsPerPe=1000, ramMb=16384, bandwidthMbps=10000, storageMb=1000000]"
                and row["vm"] == "MachineConfig[pes=2, mipsPerPe=1000, ramMb=4096, bandwidthMbps=1000, storageMb=20000]",
                f"Unexpected machine capacities: {k}")
        sla = int(row["sla_tasks"])
        feasible = int(row["intrinsically_feasible"])
        infeasible = int(row["intrinsically_infeasible"])
        misses = int(row["sla_misses"])
        feasible_misses = int(row["feasible_sla_misses"])
        batch_count = MIXES[SCENARIOS.index(scenario)][0]
        expected_sla = 0 if workload_class == "BATCH" else count - batch_count if workload_class == "ALL" else count
        require(sla == expected_sla and feasible + infeasible == sla and 0 <= feasible_misses <= feasible
                and feasible_misses + infeasible == misses and (config == CONFIGS[0] or infeasible == 0),
                f"Incorrect SLA cohort or counts: {k}")
        for numerator, denominator, name in ((misses, sla, "sla_miss_ratio"),
                                             (feasible_misses, feasible, "feasible_sla_miss_ratio")):
            expected_ratio = numerator / denominator if denominator else None
            require(metrics[name] is None if expected_ratio is None else approximately(metrics[name], expected_ratio),
                    f"Invalid ratio or undefined denominator: {k} {name}")
        start, end = float(row["measurement_start_s"]), float(row["measurement_end_s"])
        require(math.isfinite(start) and math.isfinite(end) and 0 <= start < end
                and approximately(metrics["throughput_per_s"], count / (end - start)),
                f"Invalid throughput: {k}")
        require(0 <= metrics["cpu_reserved_fraction"] <= 1 + 1e-8
                and 0 <= metrics["ram_reserved_fraction"] <= 1 + 1e-8
                and 0 <= metrics["median_response_s"] <= metrics["p95_response_s"] <= metrics["p99_response_s"]
                and metrics["mean_response_s"] + 1e-8 >= metrics["mean_queue_wait_s"], f"Invalid latency or occupancy: {k}")
        if "max_queue_wait_s" in row:
            require(metrics["max_queue_wait_s"] + 1e-8 >= metrics["mean_queue_wait_s"], f"Invalid max wait: {k}")
        fairness, makespan = metrics["jain_entitlement_normalized"], metrics["batch_makespan_s"]
        require((fairness is not None and 0 <= fairness <= 1 + 1e-8) if workload_class == "ALL" else fairness is None,
                f"Invalid fairness: {k}")
        require((makespan is not None and makespan > 0) if workload_class in ("ALL", "BATCH") else makespan is None,
                f"Invalid Batch makespan: {k}")
        if fingerprint:
            require(re.fullmatch(r"[0-9a-f]{64}", row["trace_sha256"]) and
                    row["run_id"] == f"{policy}/{scenario}/{seed}/{config}", f"Invalid trace metadata: {k}")

    for policy, scenario, config, seed in product(policies, SCENARIOS, CONFIGS, seeds):
        run = (policy, scenario, config, seed)
        aggregate = rows[(*run, "ALL")]
        classes = [rows[(*run, name)] for name in CLASSES[1:]]
        for name in (*COUNTS, "throughput_per_s", "cpu_reserved_fraction", "ram_reserved_fraction"):
            require(approximately(metric(aggregate, name), sum(metric(row, name) for row in classes)),
                    f"Aggregate does not reconcile: {run} {name}")
        for name in ("measurement_start_s", "measurement_end_s"):
            require(all(row[name] == aggregate[name] for row in classes), f"Window mismatch: {run}")


def validate(root, seeds):
    comparison_header, comparative = indexed(root / FILES[0])
    ablation_header, ablation = indexed(root / FILES[1])
    require(comparison_header == ablation_header, "Comparative and ablation schemas differ")
    validate_rows(comparative, POLICIES, seeds)
    validate_rows(ablation, VARIANTS, seeds)
    all_rows = comparative | ablation
    fingerprints = set()
    expected_runs = {}
    for scenario, config, seed in product(SCENARIOS, CONFIGS, seeds):
        run_key = scenario, config, seed
        reference = comparative["WMRF_LG", *run_key, "ALL"]
        fingerprint = reference["trace_sha256"]
        require(fingerprint not in fingerprints, f"Repeated fingerprint across workloads: {run_key}")
        fingerprints.add(fingerprint)
        for workload_class in CLASSES:
            row_key = (*run_key, workload_class)
            for policy in (*POLICIES, *VARIANTS):
                row = all_rows[policy, *row_key]
                require(all(row[name] == comparative["WMRF_LG", *row_key][name] for name in CONTROLS),
                        f"Paired inputs or denominators differ: {policy} {row_key}")
                active = {"WMRF_LG": (.20, .05), **dict(zip(VARIANTS, ((.20, .05), (0, .05), (.20, 0), (0, 0))))}
                if policy in active:
                    require((float(row["latency_coefficient"]), float(row["ageing_coefficient"])) == active[policy]
                            and float(row["ageing_threshold_s"]) == 60, f"Incorrect ablation parameters: {policy}")
                else:
                    require(all(not row[name] for name in ("latency_coefficient", "ageing_coefficient", "ageing_threshold_s")),
                            f"Inapplicable coefficients populated: {policy}")
            full = ablation[VARIANTS[0], *row_key]
            compare = comparative["WMRF_LG", *row_key]
            require(all(full[name] == compare[name] for name in (*METRICS, "measurement_end_s")),
                    f"Full ablation does not reproduce WMRF-LG: {row_key}")
        for policy in (*POLICIES, *VARIANTS):
            row = all_rows[policy, *run_key, "ALL"]
            expected_runs[row["run_id"]] = row, FILES[0 if policy in POLICIES else 1]

    _, manifest = read_csv(root / "run-manifest.csv")
    require(len(manifest) == len(expected_runs) and {r["run_id"] for r in manifest} == set(expected_runs),
            "Manifest coverage or duplicate run ID")
    for record in manifest:
        row, filename = expected_runs[record["run_id"]]
        require(record["status"] == "SUCCESS" and not record["detail"] and record["result_file"] == filename
                and all(record[name] == row[name] for name in
                        ("policy", "scenario", "seed", "configuration", "purpose", "trace_sha256",
                         "task_count", "host_count", "host", "vm_count", "vm")), f"Manifest mismatch: {record['run_id']}")
    return comparative, ablation


def validate_archives(archives, comparative):
    for policy, filename in zip(POLICIES, ("fcfs-results.csv", "strict-priority-results.csv",
                                         "weighted-drf-results.csv", "wmrf-lg-results.csv")):
        header, old = indexed(archives / filename)
        validate_rows(old, (policy,), (42, 43, 44), fingerprint=False)
        for row_key, row in old.items():
            require(all(row[column] == comparative[row_key][column] for column in header),
                    f"Historical regeneration differs: {filename} {row_key}")


def describe(values, draws):
    if not values:
        return (0, "", "", "", "", "", "", "")
    a = np.asarray(values)
    n = len(a)
    lo, hi = ("", "")
    if n > 1:
        means = a[draws[n]].mean(axis=1)
        lo, hi = np.percentile(means, [2.5, 97.5], method="linear")
    return (n, a.mean(), a.std(ddof=1) if n > 1 else "", np.median(a), a.min(), a.max(), lo, hi)


def groups(policies):
    return product(policies, SCENARIOS, CONFIGS, CLASSES, METRICS)


def summarize(rows, policies, draws):
    for policy, scenario, config, cls, field in groups(policies):
        values = [metric(rows[policy, scenario, config, seed, cls], field) for seed in SEEDS]
        yield (policy, scenario, config, cls, field, *describe([v for v in values if v is not None], draws))


def differences(rows, contrasts, draws):
    for label, scenario, config, cls, field in groups(contrasts):
        values = []
        for seed in SEEDS:
            terms = [(scale, metric(rows[policy, scenario, config, seed, cls], field))
                     for policy, scale in contrasts[label]]
            if all(value is not None for _, value in terms):
                values.append(math.fsum(scale * value for scale, value in terms))
        yield (label, scenario, config, cls, field, *describe(values, draws))


def write_table(path, header, records):
    with path.open("x", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(header)
        writer.writerows(records)


def plot_metric(path, table, policies, field, classes, label):
    colors = ("#31688e", "#d97427", "#43835b", "#8d4e9f")
    fig, axes = plt.subplots(len(classes), 2, figsize=(12, 3.05 * len(classes) + 1.15), squeeze=False)
    scenario_labels = ("Balanced", "Batch-heavy", "Interactive-heavy", "Inference-heavy")
    policy_labels = {"FCFS": "FCFS", "STRICT_PRIORITY": "Strict Priority", "WEIGHTED_DRF": "Weighted DRF",
                     "WMRF_LG": "WMRF-LG", **dict(zip(VARIANTS, ("Full", "No latency guard", "No ageing", "Neither")))}
    for i, cls in enumerate(classes):
        comparable = [table[policy, scenario, config, cls, field] for policy, scenario, config in
                      product(policies, SCENARIOS, CONFIGS)]
        upper = max((row[7] for row in comparable if row[7] != ""), default=1)
        for j, config in enumerate(CONFIGS):
            ax = axes[i, j]
            for p, (policy, color) in enumerate(zip(policies, colors)):
                for x, scenario in enumerate(SCENARIOS):
                    n, mean, _, _, _, _, lo, hi = table[policy, scenario, config, cls, field]
                    if not n:
                        continue
                    pos = x + (p - 1.5) * .15
                    if lo != "":
                        ax.vlines(pos, lo, hi, color=color, linewidth=1.7)
                    ax.plot(pos, mean, "o", color=color, ms=5.5)
            ax.set_xticks(range(len(SCENARIOS)), scenario_labels, fontsize=9)
            ax.set_ylim(0, max(upper * 1.12, .0001))
            ax.grid(axis="y", alpha=.22)
            ax.spines[["top", "right"]].set_visible(False)
            if i == 0:
                ax.set_title(("Original stress", "Unapproved demonstration candidate")[j], fontsize=11)
            if j == 0:
                name = {"ALL": "All classes", "BATCH": "Batch", "INTERACTIVE": "Interactive",
                        "AI_INFERENCE": "AI inference"}[cls]
                ax.set_ylabel(f"{name}\n{label}")
    fig.legend([plt.Line2D([], [], marker="o", color=color, linestyle="") for color in colors],
               [policy_labels[policy] for policy in policies], loc="upper center", ncol=4, frameon=False,
               bbox_to_anchor=(.5, .93))
    fig.suptitle(path.stem.replace("-", " ").capitalize(), fontsize=15, fontweight="bold", y=.985)
    fig.text(.5, .018, "Mean across 20 seeds with pointwise 95% bootstrap intervals. Missing points have no applicable denominator.",
             ha="center", fontsize=8.5)
    fig.tight_layout(rect=(0, .045, 1, .84 if len(classes) == 1 else .88))
    fig.savefig(path, dpi=165, bbox_inches="tight")
    plt.close(fig)


def make_plots(folder, comparative, ablation):
    folder.mkdir()
    series = (
        ("interactive-mean-response", "mean_response_s", ("INTERACTIVE",), "Seconds"),
        ("inference-mean-response", "mean_response_s", ("AI_INFERENCE",), "Seconds"),
        ("p95-response", "p95_response_s", ("INTERACTIVE", "AI_INFERENCE"), "Seconds"),
        ("p99-response", "p99_response_s", ("INTERACTIVE", "AI_INFERENCE"), "Seconds"),
        ("jain-fairness", "jain_entitlement_normalized", ("ALL",), "Index"),
        ("throughput", "throughput_per_s", ("ALL",), "Tasks per second"),
        ("cpu-reservation-occupancy", "cpu_reserved_fraction", ("ALL",), "Fraction"),
        ("ram-reservation-occupancy", "ram_reserved_fraction", ("ALL",), "Fraction"),
        ("batch-mean-queue-wait", "mean_queue_wait_s", ("BATCH",), "Seconds"),
        ("batch-max-queue-wait", "max_queue_wait_s", ("BATCH",), "Seconds"),
        ("batch-makespan", "batch_makespan_s", ("BATCH",), "Seconds"),
        ("intrinsically-feasible-sla-miss-ratio", "feasible_sla_miss_ratio", ("INTERACTIVE", "AI_INFERENCE"), "Ratio"),
    )
    for name, field, classes, label in series:
        plot_metric(folder / f"{name}.png", comparative, POLICIES, field, classes, label)
    for index in (0, 1, 4, 8, 9):
        name, field, classes, label = series[index]
        plot_metric(folder / f"ablation-{name}.png", ablation, VARIANTS, field, classes, label)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--archives", type=Path, required=True)
    parser.add_argument("--replay", type=Path)
    args = parser.parse_args()
    comparative, ablation = validate(args.directory, SEEDS)
    validate_archives(args.archives, comparative)
    if args.replay:
        replay_comparative, replay_ablation = validate(args.replay, (42,))
        for rows, replay in ((comparative, replay_comparative), (ablation, replay_ablation)):
            require(replay == {k: v for k, v in rows.items() if k[3] == 42}, "Seed 42 replay changed")
    rng = np.random.default_rng(BOOTSTRAP_SEED)
    draws = {n: rng.integers(0, n, size=(BOOTSTRAP_REPS, n)) for n in range(2, len(SEEDS) + 1)}
    comparative_summary = list(summarize(comparative, POLICIES, draws))
    ablation_summary = list(summarize(ablation, VARIANTS, draws))
    prefix = ("policy", "scenario", "configuration", "class", "metric")
    write_table(args.directory / "comparative-summary.csv", (*prefix, *STAT_COLUMNS), comparative_summary)
    write_table(args.directory / "wmrf-lg-ablation-summary.csv", (*prefix, *STAT_COLUMNS), ablation_summary)
    contrasts = {f"WMRF_LG_minus_{policy}": (("WMRF_LG", 1), (policy, -1)) for policy in POLICIES[:-1]}
    write_table(args.directory / "paired-comparisons.csv", ("contrast", *prefix[1:], *STAT_COLUMNS),
                differences(comparative, contrasts, draws))
    full, no_latency, no_ageing, neither = VARIANTS
    ablation_contrasts = {
        "no_latency_minus_full": ((no_latency, 1), (full, -1)),
        "no_ageing_minus_full": ((no_ageing, 1), (full, -1)),
        "neither_minus_full": ((neither, 1), (full, -1)),
        "latency_by_ageing_interaction": ((neither, 1), (no_latency, -1), (no_ageing, -1), (full, 1)),
    }
    write_table(args.directory / "wmrf-lg-ablation-contrasts.csv", ("contrast", *prefix[1:], *STAT_COLUMNS),
                differences(ablation, ablation_contrasts, draws))
    make_plots(args.directory / "plots",
               {tuple(row[:5]): row[5:] for row in comparative_summary},
               {tuple(row[:5]): row[5:] for row in ablation_summary})
    print(f"Validated {len(comparative)//4} comparative and {len(ablation)//4} ablation runs, "
          f"including all 96 historical runs and {len(SEEDS)*len(SCENARIOS)*len(CONFIGS)} matched traces.")


if __name__ == "__main__":
    main()
