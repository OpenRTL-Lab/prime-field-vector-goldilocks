<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Prime-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
File: docs/mathematical-model.md

SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 电路功能与数学模型

[English](mathematical-model.en.md)

本文件严格定义 Goldilocks 素域向量加速器的六种运算、域元素编码、任务参数、输入顺序与空向量结果，使集成方能够直接判断计算是否与目标协议一致。

## 素域与编码

所有算术在有限域 $\mathbb{F}_p$ 中进行，其中

$$
p=2^{64}-2^{32}+1=18446744069414584321.
$$

域元素以 64 位无符号规范整数 $x$ 表示，满足 $0\leq x<p$。结果也返回这一范围内的唯一代表元。数学式中的加、减、乘和求和均取模 $p$。负数不是接口输入编码；例如 $-1$ 应编码为 $p-1$。

一次任务选择模式 $m$、向量长度 $L$、项数 $K$ 与 32 位标签。配置范围为 $1\leq L_{\max}\leq65536$、$1\leq K_{\max}\leq8$，合法任务 $0\leq L\leq L_{\max}$。除 LINCOMB 外 $K=0$；LINCOMB 要求 $1\leq K\leq K_{\max}$。默认配置单通道，$L_{\max}=65536$、$K_{\max}=8$。

## 六种运算

| 模式编码 | 运算 | 输入数据记录 | 结果 |
| --- | --- | --- | --- |
| 0 | ADD | 按 $i$ 递增的 $(a_i,b_i)$ | $y_i=a_i+b_i$ |
| 1 | SUB | 按 $i$ 递增的 $(a_i,b_i)$ | $y_i=a_i-b_i$ |
| 2 | MUL | 按 $i$ 递增的 $(a_i,b_i)$ | $y_i=a_i b_i$ |
| 3 | FMA | 按 $i$ 递增的 $(a_i,b_i,c_i)$ | $y_i=a_i b_i+c_i$ |
| 4 | LINCOMB | 先输入 $K$ 个系数，再输入 $L K$ 个向量元素 | $y_i=\sum_{j=0}^{K-1}\lambda_j a_{j,i}$ |
| 5 | DOT | 按 $i$ 递增的 $(a_i,b_i)$ | $y_0=\sum_{i=0}^{L-1}a_i b_i$ |

前五种运算输出 $L$ 个元素，DOT 输出一个元素。索引 $i$ 从 0 到 $L-1$。FMA 是**有限域乘加**，不是浮点乘加。

LINCOMB 的系数记录按 $j=0,\ldots,K-1$ 输入，记录类型为 coefficient，值置于 `a`。后续数据记录按以下顺序输入：

$$
a_{0,0},a_{1,0},\ldots,a_{K-1,0},
a_{0,1},\ldots,a_{K-1,1},\ldots,
a_{0,L-1},\ldots,a_{K-1,L-1}.
$$

即输出索引 $i$ 为外层顺序、项索引 $j$ 为内层顺序；每条数据记录只使用 `a`。同一任务的 $\lambda_j$ 用于全部 $L$ 个输出。

## 空向量与任务元信息

$L=0$ 时，ADD、SUB、MUL、FMA、LINCOMB 不输出数据，DOT 输出一个值为 0、索引为 0 的结果。空 LINCOMB 仍接收 $K$ 个系数记录。

输出标签与任务标签相同；前五种模式结果索引递增，DOT 索引为 0，最后一个结果的 `last` 为 1。完成记录报告标签、状态和输出数量。

## 合法输入与错误语义

以上公式描述合法输入。参数、帧格式或规范域编码错误通过完成状态报告，完整协议见 [原生接口](interface.md)。未被对应模式使用的字段不参与算术，也不做域编码检查。使用中的非规范值替换为 0 后完成确定性的错误排空；错误任务的结果不是合法任务公式的成功输出。

该模块只执行上述六种素域运算；向量布局、挑战系数来源及证明协议由集成方定义。
