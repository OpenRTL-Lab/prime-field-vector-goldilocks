<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Prime-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
File: docs/publication-check.en.md

SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Publication checks

[简体中文](publication-check.md)

This document describes release contents and checks: complete design and simulation sources, typical product SV, tool versions, and bilingual documentation are provided together. Synthesis information is restated, without machine information, credentials, or internal engineering archives.

## Content checks

- Design, formulas, and native interfaces agree; all six modes, input ordering, and parameter boundaries are defined.
- Documents have Chinese/English pairs. English prose links to English documents, with separately marked language-switch links.
- Every document opens with a summary, ordinary links to the same target use consistent names, and relative links resolve inside the package.
- Full RTL reproduction passes, bundled SV matches regenerated circuitry, and negative controls detect deliberate corruption of expected results.
- Latency, throughput, and useful utilization are reported separately, with frequency conversion tied to documented synthesis conditions.

## Package boundary

The package includes source code, SV, file lists, scripts, the build file, version, license, and documentation. It excludes builds, caches, tool binaries, temporary logs, private work records, and Git repository internals.

Repository files and attachment contents do not contain local absolute paths, access credentials, private keys, internal source commit identifiers, internal signatures, or checksum manifests. Mathematical field constants are not credentials. GitHub-generated commit identifiers, public commit identity, and attachment integrity metadata are platform mechanisms, not private project provenance.

Release provides the complete hardware source archive and bilingual design, evidence and download information. The public-file checker is [tools/audit_public.py](../tools/audit_public.py).
