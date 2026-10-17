<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Prime-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
File: docs/toolchain.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Toolchain

[简体中文](toolchain.md)

This document lists the open-source tool versions used for independent generation and full functional/performance verification, installation requirements, and optional environment variables. Scala, Chisel, and plugin dependencies are pinned in the build file; reproduction requires no proprietary synthesis tool.

| Tool | Verified version | Role |
| --- | --- | --- |
| Mill | 1.1.7 | Scala/Chisel build |
| Scala | 2.13.18 | Design language |
| Chisel / Chisel plugin | 7.13.0 | Circuit generation |
| CIRCT firtool | 1.149.0 | SystemVerilog lowering |
| OpenJDK | 21.0.11 | JVM |
| Python | 3.12 | Scripts and independent big-integer checking |
| Verilator | 5.050 | Lint and RTL simulation |
| GCC/G++ | 16.2.0 | C reference and C++ driver compilation |

Install the tools so `java`, `mill`, `python3`, `verilator`, `cc`, and `g++` are available on PATH. Mill resolves Scala/Chisel from public Maven dependencies; the first run needs network access or your own dependency cache. Use a public firtool installation/resolution flow compatible with Chisel.

Set `MILL` if your launcher command differs, for example `MILL='bash mill-launcher'`; scripts parse it as command words. `CC` selects a single C compiler executable. Cache and temporary directories can be controlled through your environment, such as `XDG_CACHE_HOME`, `COURSIER_CACHE`, and `TMPDIR`. Machine-specific paths, proxies, caches, and tool binaries are not bundled.

Verification uses eight parallel compilation jobs by default; `--jobs` accepts 1..64 and does not change measured clock cycles. After a tool-version change, rerun generation and simulation using [Reproduction guide](reproduction.en.md).
