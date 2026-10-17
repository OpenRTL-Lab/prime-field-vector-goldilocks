<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Prime-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
File: docs/reproduction.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Reproduction guide

[简体中文](reproduction.md)

This document gives the complete workflow from tool preparation through generation, independent reference checking, product lint, four RTL test modes, and performance measurement. The default verifies both regenerated circuitry and bundled SV and produces readable cycle measurements.

## Obtain the release snapshot

[Release v1.1.0](https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks/releases/tag/v1.1.0)

```bash
git clone --branch v1.1.0 --depth 1 https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks.git
cd prime-field-vector-goldilocks
```

The version tag identifies the current source tree, including source notices and documentation. Release downloads provide hardware sources and bundled RTL for this version. Archive annotations and documentation can differ from the online tree. For RTL integration, use the product file list rather than verification environments; use the verification and synthesis documents for supported configurations and measurement conditions.

## 1. Prepare tools

Install open-source tools and configure PATH according to [Toolchain](toolchain.en.md). Run all commands below from the repository root. Outputs must be new relative directories inside `build/`; choose a new name for each rerun to retain previous logs.

Confirm the tools are callable before generation or verification:

```bash
java -version
mill --version
python3 --version
verilator --version
cc --version
g++ --version
```

`--max-l` sets maximum vector length, and `--max-k` sets maximum LINCOMB term count. `--batch` (6..8, default 6) sets full-length benchmark tasks per arithmetic mode; the complete suite also includes boundary and error tasks. `--jobs` controls compilation parallelism, not hardware lanes or task concurrency.

## 2. Complete reproduction

This command compiles the C reference, generates vectors, checks them using independent Python integer arithmetic, emits SV from Chisel, runs product lint and ideal/protocol/reset/negative tests, measures performance, and compares bundled and regenerated product code and simulation results:

```bash
python3 tools/verify.py --out build/verify --jobs 8
```

The typical configuration has maximum length 65536, maximum term count 8, and result queue depth 32. Tests contain 104 tasks, 5,112,713 input records, and 1,966,448 results. Successful verification exits with code 0.

Key outputs:

| Output (using build/verify) | Contents |
| --- | --- |
| configuration.json | Parameters and vector counts |
| results.json | Verdicts, mode latencies, throughput, and utilization |
| vectors/tasks.json | Each task's mode, length, terms, and benchmark marker |
| rtl/product.f | Generated product-only file list |
| generated/ideal.log | Task timestamps and useful-work records for regenerated product |
| bundled/ideal.log | Same measurements for bundled product |
| Other logs under generated/ and bundled/ | Compilation, protocol, reset, and negative-control tests |

`status=0` in `results.json` means all checks passed. Ideal, protocol, and reset require `mismatches=0, timedout=0, pass=1`. Negative deliberately corrupts the first reference result and requires exactly `mismatches=1, pass=0`. Its simulation exit code 1 is an expected successful detection; the overall verification command still exits with 0.

## 3. Generate circuitry only

This command generates typical product circuitry and a separate verification environment, preparing C/Python-checked vectors without running RTL simulation:

```bash
python3 tools/emit_sv.py --out build/emit --max-l 65536 --max-k 8 --batch 6
```

For integration, use only files listed in `build/emit/rtl/product.f` or the bundled [files.f](../rtl/v1.1.0/l65536-k8-lane1/files.f), excluding `PrimeVectorSimTop` and simulation initialization memories.

This command lints the bundled product independently to check top-module and dependency resolution:

```bash
verilator --lint-only --assert --top-module PrimeFieldVector -f rtl/v1.1.0/l65536-k8-lane1/files.f
```

## 4. Check another size

This command generates and verifies a smaller configuration with maximum length 17 and maximum term count 4 using the same mathematical and protocol checks:

```bash
python3 tools/verify.py --out build/verify-small --max-l 17 --max-k 4 --jobs 8
```

The generator supports `--max-l 1..65536` and `--max-k 1..8`. Bundled SV and its synthesis results remain tied to the typical configuration; other sizes can be generated and measured through the same workflow.

## 5. Read measurements

`performance.modes` maps 0..5 to the six modes and includes `latency_cycles`, `task_interval_cycles`, `tasks_per_cycle`, `output_words_per_cycle`, `unit_issues_per_task`, and `unit_busy_saturated_percent`. Definitions and measured results are in [Performance testing](performance.en.md).

Timestamps come from RTL clocks, not simulation wall time. Ideal mode measures performance; protocol backpressure mode verifies robustness without adding external constraints to ideal-interface measurements.
