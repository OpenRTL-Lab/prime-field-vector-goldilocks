<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Prime-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
File: THIRD_PARTY_NOTICES.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Third-party notices

[简体中文](THIRD_PARTY_NOTICES.md)

This document describes tool dependencies and licensing boundaries: project code is licensed under MIT, while Chisel, Mill, CIRCT, Verilator, and compiler tools are installed separately and retain their upstream licenses. No tool binaries or process libraries are bundled.

The Goldilocks modulus and six field operations are defined in [Circuit function and mathematical model](docs/mathematical-model.en.md). The C and Python reference implementations use integer modular arithmetic to check hardware results without proprietary models. The build resolves Scala, Chisel, and the Chisel plugin from public dependencies; versions are listed in [Toolchain](docs/toolchain.en.md).

When redistributing or integrating the project, retain the copyright and license notices in the [MIT License](LICENSE), and comply with licenses of upstream tools and other components you use.
