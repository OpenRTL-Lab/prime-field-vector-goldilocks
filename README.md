<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Prime-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
File: README.md

SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Goldilocks Finite-Field Vector Hardware Accelerator

[简体中文](README.zh-CN.md)

This repository provides a standalone Goldilocks prime-field vector hardware accelerator developed in **Chisel**, supporting elementwise addition, subtraction, multiplication and multiply-add, vector linear combinations, and dot products. Its native interface enables integration into ASIC or FPGA designs for polynomial-vector arithmetic, combinations of constraint values, batched field arithmetic, and other computations in zero-knowledge proofs.

It is licensed under the [MIT License](LICENSE), permitting free commercial use, modification, and redistribution, provided the copyright and license notices are retained; dependency information is provided in [Third-party notices](THIRD_PARTY_NOTICES.en.md).

[Release v1.1.0](https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks/releases/tag/v1.1.0) · [Native interface](docs/interface.en.md) · [Reproduction guide](docs/reproduction.en.md) · [Performance testing](docs/performance.en.md) · [Synthesis results](docs/synthesis-summary.en.md)

## Computation and task units

One task selects an operation on vectors of length L. K is the number of weighted input vectors in LINCOMB only, not a lane count; the bundled limits are L <= 65,536 and K <= 8. All elements are canonical unsigned 64-bit values 0 <= x < p, for Goldilocks p = 2^64 - 2^32 + 1. Arithmetic in the following table is modulo p.

| Mode | Computation and output unit |
| --- | --- |
| ADD / SUB / MUL | ADD: y_i = a_i + b_i; SUB: y_i = a_i - b_i; MUL: y_i = a_i * b_i; each returns L outputs |
| FMA | y_i = a_i * b_i + c_i; L outputs; finite-field multiply-add, not floating-point FMA |
| LINCOMB | y_i = sum_j lambda_j * a_(j,i); L outputs from K vectors with one task-wide coefficient per vector |
| DOT | sum_i a_i * b_i; one scalar output, including zero for an empty dot product |

A one-lane data record supplies the operands of one element operation. LINCOMB instead receives the K coefficient records first, then K data records for output index 0, K for index 1, and so on. Complete vector tasks do not overlap, even when element arithmetic is pipelined.

## At a glance

| Integration question | Released hardware |
| --- | --- |
| Computation | ADD, SUB, MUL, FMA, weighted LINCOMB and DOT over the Goldilocks prime field |
| Bundled configuration | Vector length L up to 65,536, LINCOMB term count K up to 8, one lane; top `PrimeFieldVector` |
| Representation | Canonical 64-bit field elements, p = 2^64 - 2^32 + 1 |
| Native boundary | Task, coefficient/data, indexed result and completion ready/valid channels |
| Scheduling | One complete vector task at a time; pipelined elements, with bounded output credits |

## Quick start

Download the source archive from [Release v1.1.0](https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks/releases/tag/v1.1.0), or clone the current release snapshot:

```bash
git clone --branch v1.1.0 --depth 1 https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks.git
cd prime-field-vector-goldilocks
```

Install the exact prerequisites in [Toolchain](docs/toolchain.en.md), then run from the repository root with a fresh output directory:

```bash
python3 tools/emit_sv.py --out build/emit --max-l 65536 --max-k 8 --batch 6
```

This emission command is not a complete RTL regression. The documented verification entry is:

```bash
python3 tools/verify.py --out build/verify --jobs 8
```

Follow [Reproduction guide](docs/reproduction.en.md) for expected results, negative-control interpretation and the distinction between short functional and full performance runs.

## Verification and integration boundary

The documented typical suite contains 104 tasks, 5,112,713 input records and 1,966,448 results. Independent C/Python references, regenerated/bundled SV comparison, ideal traffic, protocol stalls, reset recovery and a deliberate reference error are separate checks. The outer verification command requires all its expected positive and negative outcomes; a negative simulator return is not itself an overall failure.

LINCOMB consumes its coefficient records first, then K consecutive data records for each output as specified in the native interface. DOT is a reduction, not an elementwise output vector. Empty DOT returns zero. This is prime-field arithmetic, not an NTT, extension-field unit or complete constraint/proof engine. The surrounding system supplies data movement and respects task-defined frame draining on errors.

### Reading performance correctly

Under ideal native interfaces, one 65,536-element MUL task takes 65,554 cycles and the measured mean completion interval is 65,555 cycles. Useful multiplier utilization is 65,536 products per task divided by that 65,555-cycle steady interval = 99.9710%. It counts valid arithmetic issues, not merely nonempty pipeline cycles. DOT returns one result for many products; its output rate must not be confused with its internal product rate.

These are documented RTL cycle measurements under ideal native interfaces. MHz-based time/rate conversions are not board or silicon measurements. The reported Design Compiler results apply only to the documented configuration, library corner and constraints; they do not establish place-and-route signoff or system throughput. See [Performance testing](docs/performance.en.md) and [Synthesis results](docs/synthesis-summary.en.md) for conditions.

## Repository contents

- **Chisel design**: [operators/prime-field-vector/src/main/scala/zk/vector/](operators/prime-field-vector/src/main/scala/zk/vector/), containing task control, arithmetic pipelines, and verification code. [VectorEnv.scala](operators/prime-field-vector/src/main/scala/zk/vector/VectorEnv.scala) is the verification environment, not product circuitry.
- **Shared field arithmetic**: [GoldilocksArithmetic.scala](common/goldilocks/src/main/scala/zk/field/GoldilocksArithmetic.scala), providing modular-add and Goldilocks modular-multiply building blocks.
- **Generated SV**: [Product SV directory](rtl/v1.1.0/l65536-k8-lane1/), with one lane, a maximum vector length of 65536, and up to 8 terms per linear combination. The top module is [PrimeFieldVector](rtl/v1.1.0/l65536-k8-lane1/PrimeFieldVector.sv); [files.f](rtl/v1.1.0/l65536-k8-lane1/files.f) lists the six product files.
- **Reference and simulation**: [C reference model](operators/prime-field-vector/cmodel/reference.c), [Independent Python checker](operators/prime-field-vector/tools/check_reference.py), and [C++ driver](operators/prime-field-vector/sim/driver.cpp) support reference checking and RTL verification.
- **Generation and verification tools**: [tools/emit_sv.py](tools/emit_sv.py) generates circuitry; [tools/verify.py](tools/verify.py) verifies functionality and measures ideal-interface performance.
- **Design documentation**: [Hardware architecture](docs/architecture.en.md), [Native interface](docs/interface.en.md), [Circuit function and mathematical model](docs/mathematical-model.en.md), [Toolchain](docs/toolchain.en.md), [Reproduction guide](docs/reproduction.en.md), [Performance testing](docs/performance.en.md), and [Synthesis results](docs/synthesis-summary.en.md).

## Applications

Use this module when computation over the Goldilocks field with modulus $2^{64}-2^{32}+1$ involves large batches of elementwise arithmetic, weighted vector combinations, or dot products. Example integration points include, but are not limited to:

- **Polynomial coefficient or evaluation vectors**: vector addition and subtraction, pointwise products, and products added to a third vector. The target algorithm determines whether NTT, interpolation, or other transforms are needed; this module performs the corresponding field arithmetic.
- **Batch combinations of constraint values and polynomials**: combine equal-length vectors pointwise using a fixed set of coefficients within each task, for example combining precomputed constraint values with challenge coefficients.
- **Scalar reductions and dot products**: sum pairwise products of field elements for accumulation or reduction steps requiring this mathematical operation.

Integrators organize data and tasks according to their protocol and may connect this module with transforms, hashes, or other operators. Computations over another prime field require arithmetic for that field.

## Performance metrics

Under ideal interfaces, elementwise modular multiplication of 65536 elements has a single-task latency of 65554 cycles, a sustained mean completion interval of 65555 cycles, and 99.9710% useful multiplier utilization; at 500 MHz this corresponds to approximately 499.855 million output field elements/s. See [Performance testing](docs/performance.en.md).

## Mathematical model

The module performs six precisely defined operations—ADD, SUB, MUL, FMA, LINCOMB, and DOT—over the prime field $p=2^{64}-2^{32}+1$, returning canonical field elements and the original task tag. The detailed document defines ordering, empty-vector behavior, and formulas. See [Circuit function and mathematical model](docs/mathematical-model.en.md).

## Quick reproduction

The workflow covers tool preparation, Chisel generation, C/Python reference checking, functional and protocol verification, reset recovery, negative controls, and cycle-based performance measurement, including equality checks between bundled SV and regenerated circuitry. See [Reproduction guide](docs/reproduction.en.md).

## Synthesis results

The bundled configuration meets setup at 500 MHz with 10% setup clock uncertainty at the TSMC 28 nm HPC+ SSG corner. Mapped cell area is 0.027502 mm², and 99.2229% of worst setup paths per endpoint have strictly fewer than 50 logic levels. See [Synthesis results](docs/synthesis-summary.en.md).

## Publication scope

Chisel, generated product SV, generation scripts, reference models, and functional/performance verification code are reproducible. Performance testing uses ideal supply and receive timing; integrators can measure external bandwidth, memory, and resource-sharing effects in their own systems.

We are sorry that third-party licensing and engineering confidentiality prevent us from providing the process libraries, original DC Tcl/SDC, raw reports, mapped netlists, or synthesis snapshots used in our synthesis flow. [Synthesis results](docs/synthesis-summary.en.md) restates the tools, process conditions, constraints, and key results for implementation assessment.

The project delivers an independent field-vector computation module; SoC design, bus adaptation, software drivers, board flows, and complete proof protocols are integration responsibilities.

## OpenRTL and custom integration

Browse [OpenRTL](https://github.com/OpenRTL-Lab), the [ZK accelerator catalogue](https://github.com/OpenRTL-Lab/zk-accelerators) and [OpenRTL website](https://openrtl.top/) for related modules and the cooperation/contact entry. Custom datapaths, interfaces and target-platform adaptation are optional services; MIT commercial use does not require buying a service. Do not submit confidential specifications in public issues.

## Release sources and integration

The version tag identifies the current source tree, including source notices and documentation. Release downloads provide hardware sources and bundled RTL for this version. Archive annotations and documentation can differ from the online tree. For RTL integration, use the product file list rather than verification environments; use the verification and synthesis documents for supported configurations and measurement conditions.
