<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Prime-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
File: docs/publication-check.md

SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 发布内容检查

[English](publication-check.en.md)

本文件说明发布包的组成和检查范围：完整设计与仿真源码、典型产品 SV、工具版本及中英文文档一同提供，综合信息仅为人工转述，不包含机器信息、凭证或内部工程归档。

## 内容检查

- 设计、数学公式与原生接口对应；六种模式、输入顺序及参数边界均有定义。
- 中英文文档成对提供，英文正文索引英文文档，跨语言入口单独标注。
- 每个文档首段总结内容，同一目标的普通文档链接名称一致，相对链接指向包内有效文件。
- 完整 RTL 复现通过，随包 SV 与重新生成产品一致；负对照能够检测期望结果被故意修改。
- 延迟、吞吐率与有效忙率分别报告，频率换算采用综合文档中给出的条件。

## 包边界

发布包包含源码、SV、文件清单、脚本、构建文件、版本、许可和文档，不包含构建目录、缓存、工具二进制、临时日志、私有工作记录或 Git 仓库内部文件。

仓库文件与附件载荷不携带本机绝对路径、访问凭证、私钥、内部来源提交标识、内部签名或校验清单。数学域常数不是凭证。GitHub 自身的提交标识、公开提交身份和附件完整性元数据属于平台机制，不是项目内的私有追溯信息。

Release 提供完整硬件源码压缩包及双语设计、证据和下载说明；公开文件检查工具为 [tools/audit_public.py](../tools/audit_public.py)。
