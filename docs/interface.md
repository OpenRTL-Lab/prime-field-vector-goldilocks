<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Prime-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
File: docs/interface.md

SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 原生接口

[English](interface.en.md)

本文件定义独立加速器的任务、输入、结果和完成四条握手通道，给出字段宽度、帧顺序、背压与错误处理规则，可直接用于 RTL 集成和测试平台编写。

任务的 L=length 是向量元素数；K=terms 仅在 LINCOMB 中表示加权向量数。生成范围 maxL=1..65,536、maxK=1..8，任务要求 0 ≤ L ≤ maxL；LINCOMB 的 K=1..maxK，其他模式 K=0。随包上限为65,536/8。模式编码0..5依次为 ADD、SUB、MUL、FMA、LINCOMB、DOT。域元素为无符号规范整数 0 ≤ x < p，p = 2^64 - 2^32 + 1，运算均模 p；tag 只标识任务，不参与算术。

## 时钟与握手

顶层为 `PrimeFieldVector`，使用 `clock` 和同步高有效 `reset`。所有通道在时钟上升沿 `valid && ready` 时传输；发送方在等待期间保持 `valid` 和载荷稳定。信号名为 `io_<通道>_valid`、`io_<通道>_ready`、`io_<通道>_bits_<字段>`。

| 通道 | 载荷方向 | 字段与位宽 |
| --- | --- | --- |
| task | 外部 → 模块 | tag:32，mode:3，length:32，terms:16 |
| input | 外部 → 模块 | coefficient:1，a:64，b:64，c:64，validCount:2，last:1 |
| result | 模块 → 外部 | tag:32，index:32，value:64，validCount:2，last:1 |
| completion | 模块 → 外部 | tag:32，status:3，outputCount:32 |

`task/input` 的 ready 由模块驱动，`result/completion` 的 ready 由接收方驱动。模式编码及公式见 [电路功能与数学模型](mathematical-model.md)。

## 一次任务的顺序

1. 提交合法的 task；一次只能有一个完整向量任务在执行。
2. LINCOMB 先传 `terms` 个系数记录，`coefficient=1`，系数使用 `a`，该系数段的最后一条 `last=1`；其他系数记录 `last=0`。
3. 数据记录 `coefficient=0`、`validCount=1`，按数学模型排序。最后一条数据记录 `last=1`，其余 `last=0`。ADD/SUB/MUL/DOT 使用 a/b，FMA 使用 a/b/c，LINCOMB 使用 a。
4. 接收结果，`validCount=1`，标签原样返回，最后一个结果 `last=1`。
5. 接收完成记录后，可以提交下一任务；`outputCount` 是实际已被接收的结果数量。

系数记录也要求 `validCount=1`。LINCOMB 的两个分段分别标记自己的 `last`，不是只在整任务末尾标记。空向量无数据记录；空 LINCOMB 仍有系数段，空 DOT 输出一个零结果。

合法非空输入记录数为：ADD/SUB/MUL/FMA/DOT 的 $L$，LINCOMB 的 $K+LK$。LINCOMB 每个输出对应连续的 $K$ 条数据。

## 状态和排空

| status | 含义 | 行为 |
| --- | --- | --- |
| 0 | 成功 | 返回规范结果和完成记录 |
| 1 | 参数错误 | mode≥6、length 超限或 terms 不合法；不接收输入，不输出结果，报告完成 |
| 2 | 帧错误 | coefficient、validCount 或 last 与预期不符；按预定记录数消费并排空 |
| 3 | 域编码错误 | 使用中的 a/b/c≥p；对应值替换为 0 后消费并排空 |

优先级为参数错误 > 帧错误 > 域编码错误。未使用的字段不做域编码检查。错误不会触发按提前 `last` 截断或按缺失 `last` 延长的变长解析；外部仍须提供任务定义的记录数量。

## 背压与复位

接收方可以暂停 result 或 completion。模块维持阻塞载荷，依据信用控制 input.ready，避免结果丢失或队列溢出。协议验证包含输入间歇、结果背压与完成背压；性能测量则采用理想接口。

同步复位中止在途任务，清空有效控制、信用及队列状态，不产生旧任务的完成记录。复位后可以重新提交任务。外部必须将复位当作任务流中止边界，而不是等待旧标签继续完成。

产品文件清单为 [files.f](../rtl/v1.1.0/l65536-k8-lane1/files.f)。验证环境的参考存储、日志、计数与驱动不属于产品接口。
