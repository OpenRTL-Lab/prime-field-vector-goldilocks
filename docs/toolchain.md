<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Prime-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
File: docs/toolchain.md

SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 工具链说明

[English](toolchain.en.md)

本文件列出实际用于独立生成和完整功能/性能复测的开源工具版本、安装要求与可选环境变量；项目构建文件固定 Scala、Chisel 与编译插件依赖，用户不需要专有综合工具即可复现。

| 工具 | 验证版本 | 用途 |
| --- | --- | --- |
| Mill | 1.1.7 | 构建 Scala/Chisel |
| Scala | 2.13.18 | 设计语言 |
| Chisel／Chisel 编译插件 | 7.13.0 | 电路生成 |
| CIRCT firtool | 1.149.0 | SystemVerilog 降级 |
| OpenJDK | 21.0.11 | JVM |
| Python | 3.12 | 脚本与独立大整数检查 |
| Verilator | 5.050 | lint 与 RTL 仿真 |
| GCC/G++ | 16.2.0 | C 模型与 C++ 驱动编译 |

安装上述工具，使 `java`、`mill`、`python3`、`verilator`、`cc` 和 `g++` 可从 PATH 调用。Mill 构建从公开 Maven 依赖解析 Scala/Chisel；首次运行需要网络或用户自己的依赖缓存。firtool 使用与 Chisel 匹配的公开安装/解析流程。

若 Mill 启动器命令不同，可设置 `MILL`，例如 `MILL='bash mill-launcher'`；脚本按命令词解析此变量。`CC` 可指定单个 C 编译器可执行程序，缓存与临时目录可由 `XDG_CACHE_HOME`、`COURSIER_CACHE`、`TMPDIR` 等用户环境配置。仓库不提供机器专用路径、代理设置、缓存或工具二进制。

验证使用 8 个编译并行任务；可通过 `--jobs` 调整为 1..64，不改变时钟周期统计。工具版本变更后，可按 [复现指南](reproduction.md) 重新验证生成和仿真。
