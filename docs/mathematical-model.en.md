<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Prime-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
File: docs/mathematical-model.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Circuit function and mathematical model

[简体中文](mathematical-model.md)

This document precisely defines the six operations, field-element encoding, task parameters, input ordering, and empty-vector results of the Goldilocks prime-field vector accelerator, allowing integrators to determine compatibility with their target computation.

## Prime field and encoding

All arithmetic takes place in $\mathbb{F}_p$, where

$$
p=2^{64}-2^{32}+1=18446744069414584321.
$$

A field element is a canonical unsigned 64-bit integer $x$ with $0\leq x<p$. Outputs use the unique representative in the same range. All additions, subtractions, multiplications, and sums in the formulas are modulo $p$. Negative integers are not interface encodings; for example, encode $-1$ as $p-1$.

A task selects mode $m$, length $L$, term count $K$, and a 32-bit tag. Configuration ranges are $1\leq L_{\max}\leq65536$ and $1\leq K_{\max}\leq8$, with legal task length $0\leq L\leq L_{\max}$. Modes other than LINCOMB require $K=0$; LINCOMB requires $1\leq K\leq K_{\max}$. The default has one lane, $L_{\max}=65536$, and $K_{\max}=8$.

## Six operations

| Mode | Operation | Data records | Result |
| --- | --- | --- | --- |
| 0 | ADD | $(a_i,b_i)$ in increasing $i$ | $y_i=a_i+b_i$ |
| 1 | SUB | $(a_i,b_i)$ in increasing $i$ | $y_i=a_i-b_i$ |
| 2 | MUL | $(a_i,b_i)$ in increasing $i$ | $y_i=a_i b_i$ |
| 3 | FMA | $(a_i,b_i,c_i)$ in increasing $i$ | $y_i=a_i b_i+c_i$ |
| 4 | LINCOMB | $K$ coefficient records, then $L K$ vector elements | $y_i=\sum_{j=0}^{K-1}\lambda_j a_{j,i}$ |
| 5 | DOT | $(a_i,b_i)$ in increasing $i$ | $y_0=\sum_{i=0}^{L-1}a_i b_i$ |

The first five operations return $L$ elements; DOT returns one. Index $i$ runs from 0 to $L-1$. FMA is **finite-field multiply-add**, not floating-point multiply-add.

LINCOMB receives coefficients in order $j=0,\ldots,K-1$ as coefficient records using `a`. Subsequent data records have this order:

$$
a_{0,0},a_{1,0},\ldots,a_{K-1,0},
a_{0,1},\ldots,a_{K-1,1},\ldots,
a_{0,L-1},\ldots,a_{K-1,L-1}.
$$

Output index $i$ is the outer order and term index $j$ the inner order; each data record uses only `a`. Coefficients $\lambda_j$ apply to all $L$ outputs of the task.

## Empty vectors and metadata

For $L=0$, ADD, SUB, MUL, FMA, and LINCOMB return no data, while DOT returns one zero-valued result at index 0. Empty LINCOMB still receives $K$ coefficient records.

Each output retains the task tag. Result indices increase in the first five modes and are 0 in DOT; the final result has `last=1`. Completion reports the tag, status, and output count.

## Legal inputs and error semantics

The formulas describe legal inputs. Parameter, framing, and noncanonical field-encoding errors are reported through completion status; the full protocol is in [Native interface](interface.en.md). Unused fields do not participate in arithmetic or field-encoding checks. Used noncanonical values are replaced by zero for deterministic error draining; error-task results are not successful outputs of the legal-input formulas.

The module performs exactly these six field operations. Vector layout, challenge-coefficient sources, and proof protocols are defined by the integrator.
