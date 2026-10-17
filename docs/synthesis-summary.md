<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Prime-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
File: docs/synthesis-summary.md

SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 综合结果

[English](synthesis-summary.en.md)

本文件转述典型单通道配置的 DC 综合条件与关键结果：TSMC 28 nm HPC+ SSG 0.81 V、−40°C，500 MHz、10% setup 时钟不确定度下 setup 通过，总映射 cell 面积 0.027502 mm²，99.2229% 的端点最差 setup 路径严格少于 50 级。

maxL 为向量长度上限，maxK 为 LINCOMB 项数上限，queueDepth 为结果记录队列深度。WNS 是最差 setup 裕量，TNS 是负 setup 裕量之和；它们是时序指标，不是流水线周期数。

## 工具与约束

| 项目 | 设置 |
| --- | --- |
| 顶层 | PrimeFieldVector |
| 版本与尺寸 | 1.1.0，maxL=65536，maxK=8，lane=1，queueDepth=32 |
| 综合工具 | Synopsys Design Compiler NXT X-2025.06-SP3 |
| 工艺 | TSMC 28 nm HPC+，SSG，0.81 V，−40°C |
| 时钟 | 500 MHz，周期 2.0 ns |
| setup 时钟不确定度 | 0.2 ns，周期的 10% |
| 时钟转换时间 | 0.1 ns |
| 输入/输出最大延迟 | 1.0 ns / 1.0 ns |
| 输入/输出最小延迟 | 0 ns / 0 ns |
| 输入转换时间 | 0.125 ns |
| 最大数据转换时间 | 0.25 ns |
| 输出 pin 负载 | 0.02 pF |
| 最大扇出约束 | 32 |
| 复位 | 同步复位，纳入时序分析 |

10% 条件在综合中以 建立时间时钟不确定度 表达。综合结果来自该产品配置及上述约束，不从仿真运行速度推算。本文报告 setup 与数据路径级数；功能与周期测量见 [性能测试](performance.md)。

## Setup 与逻辑级数

| 指标 | 结果 |
| --- | ---: |
| Setup | 通过 |
| WNS | +0.000036478 ns |
| TNS | 0 ns |
| 违例端点 | 0 |
| 已约束 setup 端点 | 8750 |
| 严格少于 50 级的路径 | 8682 / 8750，99.2229% |
| 恰好 50 级的路径 | 8 |
| 最大逻辑级数 | 67 |

每个已约束端点选择最差的一条 setup max 数据路径，分母为 8750，包括 7552 条内部到内部、199 条内部到输出、999 条输入到内部路径。每个数据路径中叶引脚名严格等于 `Z` 或 `ZN` 的点计一级；其他引脚、net 行、clock-to-Q 和时钟树不计入，报告行数不代表级数。路径点与完整报告的对应序列已交叉核对。

| 累计阈值 | 数量 | 比例 |
| --- | ---: | ---: |
| >30 级 | 378 | 4.3200% |
| >40 级 | 196 | 2.2400% |
| >50 级 | 60 | 0.6857% |
| >60 级 | 12 | 0.1371% |

各阈值为累计统计，不能相加；严格小于 50 也不等于小于等于 50。

## 面积与单元构成

| 互不重叠分类 | 面积 µm² | 占总面积 |
| --- | ---: | ---: |
| 组合逻辑，不含缓冲/反相 | 12590.844 | 45.7818% |
| 缓冲/反相 | 664.342 | 2.4156% |
| 非组合逻辑 | 14246.652 | 51.8025% |
| SRAM 宏 / Black Box | 0 | 0% |
| 总计 | 27501.838 | 100% |

下表按功能层次列出包含子层次的面积；父子面积重叠，不能相加。

| 功能层次 | 面积 µm² | 占顶层 |
| --- | ---: | ---: |
| 32 项结果队列 | 10847.130 | 39.4415% |
| 队列中的存储子层次 | 10812.732 | 39.3164% |
| 模乘流水线 | 9189.656 | 33.4147% |
| 模加减流水线 | 1064.378 | 3.8702% |
| 输出寄存队列 | 307.622 | 1.1186% |

总 cell 面积为映射实例面积之和，不是含布线、利用率和留白的芯片面积。此配置没有 SRAM 物理宏；队列以该配置映射得到的普通逻辑/时序单元实现。

## 提供形式

很抱歉，受第三方授权与工程资料保密要求限制，我们无法提供工艺库、原始综合脚本、原始报告、映射网表或快照。本文提供经过核对的条件与结果转述；RTL 与仿真链路可按 [复现指南](reproduction.md) 完整复现。
