<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Prime-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
File: docs/synthesis-summary.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Synthesis results

[简体中文](synthesis-summary.md)

This document restates DC conditions and key results for the typical single-lane configuration: setup passes at 500 MHz with 10% setup clock uncertainty at TSMC 28 nm HPC+ SSG, 0.81 V, −40°C. Total mapped cell area is 0.027502 mm², and 99.2229% of worst setup paths per endpoint have strictly fewer than 50 logic levels.

maxL bounds vector length, maxK bounds LINCOMB terms, and queueDepth counts buffered result records. WNS is minimum setup slack; TNS sums negative setup slacks. These are timing metrics, not pipeline cycle counts.

## Tools and constraints

| Item | Setting |
| --- | --- |
| Top | PrimeFieldVector |
| Version and size | 1.1.0, maxL=65536, maxK=8, lane=1, queueDepth=32 |
| Synthesis tool | Synopsys Design Compiler NXT X-2025.06-SP3 |
| Process | TSMC 28 nm HPC+, SSG, 0.81 V, −40°C |
| Clock | 500 MHz, period 2.0 ns |
| Setup clock uncertainty | 0.2 ns, 10% of period |
| Clock transition | 0.1 ns |
| Maximum input/output delay | 1.0 ns / 1.0 ns |
| Minimum input/output delay | 0 ns / 0 ns |
| Input transition | 0.125 ns |
| Maximum data transition | 0.25 ns |
| Output pin load | 0.02 pF |
| Maximum fanout constraint | 32 |
| Reset | Synchronous, included in timing analysis |

The 10% condition is expressed as setup clock uncertainty. Results correspond to this product and these constraints, not simulation execution speed. This document reports setup and data-path depth; functional and cycle measurements are in [Performance testing](performance.en.md).

## Setup and logic levels

| Metric | Result |
| --- | ---: |
| Setup | Pass |
| WNS | +0.000036478 ns |
| TNS | 0 ns |
| Violating endpoints | 0 |
| Constrained setup endpoints | 8750 |
| Paths strictly below 50 levels | 8682 / 8750, 99.2229% |
| Paths at exactly 50 levels | 8 |
| Maximum depth | 67 |

The worst setup max data path is selected for each constrained endpoint. The denominator is 8750: 7552 internal-to-internal, 199 internal-to-output, and 999 input-to-internal paths. Each data-path point whose leaf pin name is exactly `Z` or `ZN` counts as one level. Other pins, net rows, clock-to-Q, and clock-tree points are excluded; report line count is not logic depth. Timing-point sequences were cross-checked against full reports.

| Cumulative threshold | Count | Percentage |
| --- | ---: | ---: |
| >30 levels | 378 | 4.3200% |
| >40 levels | 196 | 2.2400% |
| >50 levels | 60 | 0.6857% |
| >60 levels | 12 | 0.1371% |

Threshold counts are cumulative and must not be added. Strictly below 50 is distinct from at most 50.

## Area and units

| Nonoverlapping category | Area µm² | Share |
| --- | ---: | ---: |
| Combinational excluding buffers/inverters | 12590.844 | 45.7818% |
| Buffers/inverters | 664.342 | 2.4156% |
| Noncombinational | 14246.652 | 51.8025% |
| SRAM macros / Black Boxes | 0 | 0% |
| Total | 27501.838 | 100% |

The following hierarchical areas include children; parent and child rows overlap and must not be added.

| Functional hierarchy | Area µm² | Share of top |
| --- | ---: | ---: |
| 32-entry result queue | 10847.130 | 39.4415% |
| Storage inside result queue | 10812.732 | 39.3164% |
| Modular multiplier pipeline | 9189.656 | 33.4147% |
| Modular add/sub pipeline | 1064.378 | 3.8702% |
| Output register queue | 307.622 | 1.1186% |

Total cell area sums mapped instance areas, not routing, utilization, or die whitespace. This configuration has no physical SRAM macro; the queue maps to ordinary combinational/sequential cells in this run.

## Publication format

We are sorry that third-party licensing and engineering confidentiality prevent providing process libraries, original synthesis scripts, raw reports, mapped netlists, or snapshots. This document restates checked conditions and results; RTL and simulation can be reproduced using [Reproduction guide](reproduction.en.md).
