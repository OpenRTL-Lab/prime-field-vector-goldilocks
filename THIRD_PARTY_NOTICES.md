<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Prime-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
File: THIRD_PARTY_NOTICES.md

SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 第三方声明

[English](THIRD_PARTY_NOTICES.en.md)

本文件说明开源工具依赖与许可边界：项目代码采用 MIT 许可证，Chisel、Mill、CIRCT、Verilator 及编译工具由用户单独安装，各自适用其上游许可；仓库不捆绑工具二进制或工艺库。

Goldilocks 模数及六类域运算的精确定义见 [电路功能与数学模型](docs/mathematical-model.md)。C 与 Python 参考实现使用整数模运算核对硬件结果，不依赖专有模型。构建文件从公开依赖解析 Scala、Chisel 与 Chisel 插件，具体版本见 [工具链说明](docs/toolchain.md)。

分发或集成时请保留 [MIT 许可证](LICENSE) 的版权与许可声明，并遵守自行使用的上游工具及其他组件的许可。
