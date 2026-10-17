<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Prime-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
File: docs/architecture.md

SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 硬件架构

[English](architecture.en.md)

本文件介绍单通道流式域向量架构：共享模乘与模加流水线以每周期一条数据的发射能力完成六种运算，使用局部系数与归约状态而无需保存完整输入向量，并以信用控制支持输出背压。

L 是任务向量长度，K 仅为 LINCOMB 的加权向量数，不是并行通道数。ADD/SUB/MUL/FMA 逐元素运算，LINCOMB 在每个索引处返回加权和，DOT 将全部成对乘积求和为一个标量。算术使用 p = 2^64 - 2^32 + 1 下的规范域元素；精确公式和顺序见 [电路功能与数学模型](mathematical-model.md)。

## 数据通路

输入先经过 2 clk 的寄存与规范域编码检查，再进入对应算术通路。模块按任务模式共享资源，不重复实例化六套完整算术。

| 单元 | 结构与用途 | 算术流水线延迟 | 发射间隔 |
| --- | --- | --- | --- |
| VectorMul | 四个 32×32 部分积与 Goldilocks 专用整数约减；MUL/FMA/LINCOMB/DOT 共用 | 11 clk | 1 clk |
| VectorAddSub | 模加减；ADD/SUB/FMA 共用 | 3 clk | 1 clk |
| 归约反馈加法器 | 并行候选/前缀式模加；LINCOMB/DOT 共用 | 1 clk | 1 clk |

FMA 的乘与加串接为 14 clk 算术流水线。上述延迟不包含任务、输入登记、输出队列和完成握手；完整任务延迟见 [性能测试](performance.md)。

LINCOMB 每个输出先安装第一个乘积，再累加后续 $K-1$ 个乘积；DOT 安装第一个乘积后累加其余 $L-1$ 个。对于非空向量，有效归约次数分别为 $L(K-1)$ 和 $L-1$。空向量不执行归约加法；空 DOT 输出零。

## 存储与背压

产品保存最多 8 个 64 位任务系数和一个 64 位归约累加值，不存储完整向量。32 项结果 FIFO 每项为 131 位，另有一级输出寄存器。信用计数覆盖在途算术、FIFO 与输出寄存器，限制总未消费结果；不能将队列项数简单相加作为可无限发射的信用。

输出阻塞时，控制器依据剩余信用暂停输入接收；已发射流水线可安全排空。数据结果及完成载荷在 `valid=1, ready=0` 时保持稳定。

## 流水线层次与生成

同一任务内部的域元素运算是流水化的，可重叠处理多个元素。控制器一次执行一个完整向量任务，在上一任务完成记录被接收后接收下一任务；任务间隔由持续任务实测，而不是由算术流水线级数推定。

Chisel 配置支持最大长度和最大项数调整；公开生成工具支持 `--max-l` 和 `--max-k`，默认 65536/8。六种模式均属于同一个算子，单通道设计保持相同原生协议。

产品由六个 SV 文件构成，采用普通整数与寄存器/队列结构，不使用 FPGA 原语、特定 SRAM 宏或初始化文件。参考向量存储和 `$readmem` 仅属于验证环境；使用 [files.f](../rtl/v1.1.0/l65536-k8-lane1/files.f) 集成产品即可排除验证顶层。
