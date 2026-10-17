<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Prime-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
File: docs/performance.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Performance testing

[简体中文](performance.md)

This document independently defines and reports task latency, sustained task throughput, and useful arithmetic utilization. Under ideal interfaces, a 65536-element modular-multiply task takes 65554 cycles with a mean completion interval of 65555 cycles and 99.9710% useful multiplier utilization; all six modes pass independent reference checking and bundled-circuit verification.

L denotes vector length; K denotes the number of weighted vectors in LINCOMB only. ADD/SUB/MUL/FMA are elementwise operations, LINCOMB returns L weighted sums, and DOT reduces L products to one scalar. Exact formulas appear in [Circuit function and mathematical model](mathematical-model.en.md). Frequency-based conversions are not board or silicon measurements.

## Configuration and method

The product has one lane, maximum length 65536, maximum term count 8, and a 32-entry result queue. Use the complete command in [Reproduction guide](reproduction.en.md); versions are listed in [Toolchain](toolchain.en.md). An independent C integer model generates reference results and a Python big-integer model checks them. RTL checking covers each value, tag, index, valid count, last marker, and completion status.

Typical tests contain 104 tasks, 5,112,713 input records, and 1,966,448 results, covering all modes, empty vectors, lengths 1/7/17/65536, term-count boundaries, field boundaries, random data, invalid parameters, framing errors, and noncanonical encodings. Ideal, protocol, reset, and negative tests run separately. The three normal modes have no mismatches; corrupting the first expected result yields exactly one detected mismatch. Bundled product SV and regenerated circuitry produce identical functional results and cycle metrics. A smaller configuration with maxL=17 and maxK=4 also passes the same workflow.

**Only ideal mode supplies performance measurements**: senders assert valid whenever data is available, and result/completion receivers continuously assert ready. No external bandwidth, latency, or backpressure constraints are introduced. Backpressure in protocol mode is a functional test, not an input to the performance tables.

## Definitions

Timestamps are clock indices at handshake edges: $A$ is task acceptance, $O$ is final-result acceptance, and $C$ is completion acceptance.

- **Task latency**: $T=C-A$, including task registration, coefficients, all data computation, output draining, and completion. $O-A$ is also reported.
- **Sustained completion interval**: submit six identical-sized tasks per mode and use completions numbered 1 through 4 for three intervals: $I=(C_4-C_1)/3$, excluding the first and last tasks.
- **Task throughput**: $3/(C_4-C_1)$ tasks/cycle; multiply by frequency $f$ for tasks/s.
- **Result throughput**: outputs per task times task throughput. DOT returns one result; $L/I$ additionally reports processed products/cycle.
- **Useful utilization**: with $N_u$ truly issued useful operations per task, use $100N_u/I$ for the sustained window. Logic toggling, nonempty pipelines, and simulation wall time are not substitutes for useful-work counts.

WORK events in the verification layer supply counters; FRAME records supply timestamps. No observation ports or work counters are added to the product. Multiplication, addition/subtraction, and reduction-feedback units have separate valid issue counts. Installing the first product into an accumulator is not a reduction addition.

## Latency and throughput

All modes use $L=65536$; LINCOMB uses $K=8$. The 500 MHz columns convert cycles at 2 ns/cycle.

| Mode | Completion latency cycles | Final-result latency cycles | Mean interval cycles | Output elements/cycle | Latency µs @500 MHz | Tasks/s @500 MHz |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| ADD | 65546 | 65544 | 65547 | 0.999832181 | 131.092 | 7628.114 |
| SUB | 65546 | 65544 | 65547 | 0.999832181 | 131.092 | 7628.114 |
| MUL | 65554 | 65552 | 65555 | 0.999710167 | 131.108 | 7627.183 |
| FMA | 65557 | 65555 | 65558 | 0.999664419 | 131.114 | 7626.834 |
| LINCOMB | 524315 | 524313 | 524316 | 0.124993325 | 1048.630 | 953.623 |
| DOT | 65555 | 65553 | 65556 | 0.000015254 | 131.110 | 7627.067 |

Each LINCOMB result requires eight products; processing throughput is 0.999946597 products/cycle, approximately 499.973 million/s @500 MHz. Each DOT result reduces 65536 products, processing 0.999694917 products/cycle, approximately 499.847 million/s @500 MHz. Output count and internal useful computation count are distinct metrics.

These are measured RTL simulation cycles; frequency conditions for 500 MHz are stated in [Synthesis results](synthesis-summary.en.md). Arithmetic within tasks is pipelined, while this version does not overlap complete vector tasks. Latency and sustained completion intervals are measured separately.

## Useful work and idle cycles

| Mode | Useful multiplications/task | Add/sub operations | Reduction additions | Multiplier utilization | Add/sub utilization | Reduction utilization |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| ADD | 0 | 65536 | 0 | 0% | 99.983218% | 0% |
| SUB | 0 | 65536 | 0 | 0% | 99.983218% | 0% |
| MUL | 65536 | 0 | 0 | 99.971017% | 0% | 0% |
| FMA | 65536 | 65536 | 0 | 99.966442% | 99.966442% | 0% |
| LINCOMB | 524288 | 0 | 458752 | 99.994660% | 0% | 87.495327% |
| DOT | 65536 | 0 | 65535 | 99.969492% | 0% | 99.967966% |

0% means a mode does not use that unit. Non-useful-issue cycles within one task equal $T-N_u$, including registration and draining; this denominator differs from the sustained-window utilization above.

| Mode | Multiplier non-useful-issue cycles | Add/sub non-useful-issue cycles | Reduction non-useful-issue cycles |
| --- | ---: | ---: | ---: |
| ADD | 65546 | 10 | 65546 |
| SUB | 65546 | 10 | 65546 |
| MUL | 18 | 65554 | 65554 |
| FMA | 21 | 21 | 65557 |
| LINCOMB | 27 | 524315 | 65563 |
| DOT | 19 | 65555 | 20 |

Long-vector modes using multiplication all exceed 99.96% useful multiplier utilization. Unit structure and area distribution are described in [Hardware architecture](architecture.en.md) and [Synthesis results](synthesis-summary.en.md).

## Measurement windows and verdicts

| Mode | $C_1$ | $C_4$ | Intervals |
| --- | ---: | ---: | ---: |
| ADD | 132978 | 329619 | 3 |
| SUB | 526260 | 722901 | 3 |
| MUL | 919558 | 1116223 | 3 |
| FMA | 1312894 | 1509568 | 3 |
| LINCOMB | 2623758 | 4196706 | 3 |
| DOT | 4852134 | 5048802 | 3 |

| Test mode | Checked results | Mismatches | Simulation cycles | Expected verdict |
| --- | ---: | ---: | ---: | --- |
| ideal | 1966448 | 0 | 5114359 | Pass |
| protocol | 1966448 | 0 | 7656163 | Pass |
| reset | 1966448 | 0 | 5114460 | Pass |
| negative | 1966448 | 1 | 5114359 | Deliberate error detected |

The 17/4 configuration has 104 tasks, 1555 input records, and 878 results and passes all four checks. Reproduce full measurements in `build/verify/results.json`; logs retain task timestamps and useful-work events.
