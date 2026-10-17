<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Prime-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
File: docs/interface.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Native interface

[简体中文](interface.md)

This document specifies the task, input, result, and completion handshake channels, including field widths, frame ordering, backpressure, and error handling, for direct RTL integration and testbench development.

A task uses L=length elements and K=terms weighted vectors for LINCOMB. Generation permits maxL=1..65,536 and maxK=1..8; tasks allow 0 <= L <= maxL, K=1..maxK for LINCOMB and K=0 otherwise. The bundled limits are 65,536/8. Mode codes 0..5 mean ADD, SUB, MUL, FMA, LINCOMB, DOT. Field values are canonical unsigned integers 0 <= x < p, with p = 2^64 - 2^32 + 1; arithmetic is modulo p. The tag identifies the task and is not an arithmetic operand.

## Clock and handshakes

The top module is `PrimeFieldVector`, with `clock` and synchronous active-high `reset`. A transfer occurs on a rising edge with `valid && ready`; senders retain valid and payload while waiting. Names follow `io_<channel>_valid`, `io_<channel>_ready`, and `io_<channel>_bits_<field>`.

| Channel | Payload direction | Fields and widths |
| --- | --- | --- |
| task | External → module | tag:32, mode:3, length:32, terms:16 |
| input | External → module | coefficient:1, a:64, b:64, c:64, validCount:2, last:1 |
| result | Module → external | tag:32, index:32, value:64, validCount:2, last:1 |
| completion | Module → external | tag:32, status:3, outputCount:32 |

The module drives task/input ready; receivers drive result/completion ready. Mode encodings and formulas are in [Circuit function and mathematical model](mathematical-model.en.md).

## Task sequence

1. Submit a legal task; only one complete vector task executes at a time.
2. For LINCOMB, send `terms` coefficient records first, with `coefficient=1` and coefficient in `a`. The last coefficient has `last=1`; preceding coefficients have `last=0`.
3. Send data records with `coefficient=0, validCount=1` in mathematical-model order. Only the final data record has `last=1`. ADD/SUB/MUL/DOT use a/b, FMA uses a/b/c, and LINCOMB uses a.
4. Receive results with `validCount=1`, the original tag, and `last=1` on the final result.
5. After consuming completion, submit the next task; `outputCount` is the number of consumed results.

Coefficient records also require `validCount=1`. The coefficient and data segments of LINCOMB each mark their own last record. Empty vectors have no data records; empty LINCOMB still has coefficients, and empty DOT returns one zero result.

Legal nonempty input counts are $L$ for ADD/SUB/MUL/FMA/DOT and $K+LK$ for LINCOMB. Each LINCOMB output corresponds to $K$ consecutive data records.

## Status and draining

| status | Meaning | Behavior |
| --- | --- | --- |
| 0 | Success | Return canonical results and completion |
| 1 | Parameter error | mode≥6, excessive length, or invalid terms; accept no input, return no results, report completion |
| 2 | Framing error | coefficient, validCount, or last differs from expectations; consume the predetermined count and drain |
| 3 | Field-encoding error | A used a/b/c is ≥p; substitute 0, consume and drain |

Priority is parameter > framing > field-encoding error. Unused fields are not checked for canonical encoding. An early last does not shorten the input count, and a missing last does not extend it; the sender must still supply the task-defined number of records.

## Backpressure and reset

Receivers may stall result or completion. The module retains blocked payloads and controls input.ready through credits to avoid loss or queue overflow. Protocol tests include input gaps, result stalls, and completion stalls; performance tests use ideal interfaces.

Synchronous reset aborts in-flight tasks and clears valid control, credits, and queue state without completing old tasks. New tasks may be submitted afterward. Integration must treat reset as an aborted task-stream boundary, not wait for an old tag to complete.

Product files are listed in [files.f](../rtl/v1.1.0/l65536-k8-lane1/files.f). Reference memories, logging, counters, and drivers in the verification environment are not product interfaces.
