<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Prime-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
File: docs/architecture.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Hardware architecture

[简体中文](architecture.md)

This document describes a single-lane streaming field-vector architecture: shared modular-multiply and modular-add pipelines issue one data record per cycle, local coefficient and reduction state avoid storing entire input vectors, and credit-based control supports output backpressure.

Here L is the task vector length and K is the number of weighted vectors in LINCOMB, not parallel lanes. ADD/SUB/MUL/FMA operate elementwise; LINCOMB returns a weighted sum at each index, while DOT returns one sum of pairwise products. Arithmetic uses canonical field elements modulo p = 2^64 - 2^32 + 1. See [Circuit function and mathematical model](mathematical-model.en.md) for exact formulas and ordering.

## Datapath

Inputs pass through two cycles of registration and canonical-field checks before entering arithmetic. Resources are shared by task mode rather than instantiated as six complete datapaths.

| Unit | Structure and role | Arithmetic latency | Issue interval |
| --- | --- | --- | --- |
| VectorMul | Four 32×32 partial products and Goldilocks integer reduction; shared by MUL/FMA/LINCOMB/DOT | 11 cycles | 1 cycle |
| VectorAddSub | Modular addition/subtraction; shared by ADD/SUB/FMA | 3 cycles | 1 cycle |
| Reduction-feedback adder | Parallel-candidate/prefix modular addition; shared by LINCOMB/DOT | 1 cycle | 1 cycle |

FMA chains multiplication and addition into a 14-cycle arithmetic pipeline. These latencies exclude task control, input registration, output queues, and completion handshakes; complete task latency is reported in [Performance testing](performance.en.md).

LINCOMB installs the first product for each output and accumulates the remaining $K-1$ products. DOT installs its first product and accumulates the remaining $L-1$. For nonempty vectors, useful reduction counts are therefore $L(K-1)$ and $L-1$, respectively. An empty vector performs no reduction additions; empty DOT emits zero.

## Storage and backpressure

The product stores up to eight 64-bit task coefficients and one 64-bit reduction accumulator, not whole vectors. A 32-entry result FIFO holds 131 bits per entry, followed by a one-entry output register. Credits cover in-flight arithmetic, the FIFO, and the output register; simply adding queue capacities does not define unconstrained issue capacity.

When output is blocked, remaining credits determine whether input can be accepted, allowing already-issued arithmetic to drain safely. Result and completion payloads remain stable when `valid=1, ready=0`.

## Pipeline levels and generation

Field arithmetic within a task is pipelined, so multiple elements overlap. Task control executes one complete vector at a time and accepts the next task after completion has been consumed. Sustained task intervals are measured, not inferred from arithmetic pipeline depth.

Chisel supports configurable maximum length and term count. The public generator exposes `--max-l` and `--max-k`, defaulting to 65536/8. All six modes belong to one operator with the same native protocol and one lane.

The product consists of six SV files using ordinary integer logic, registers, and queues, without FPGA primitives, a technology-specific SRAM macro, or initialization files. Reference-vector storage and `$readmem` belong only to verification; integrating the product through [files.f](../rtl/v1.1.0/l65536-k8-lane1/files.f) excludes the verification top.
