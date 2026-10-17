<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Prime-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
File: README.zh-CN.md

SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# prime-field-vector-goldilocks 硬件加速器

[English](README.md)

本仓库提供使用 **Chisel** 开发的 Goldilocks 素域向量独立硬件加速器，支持逐元素加、减、乘、乘加，以及向量线性组合和点积。它可通过原生接口集成到 ASIC 或 FPGA，用于零知识证明中的多项式向量运算、约束值组合、批量域运算等计算。

采用 [MIT 许可证](LICENSE)，允许免费商用、修改和再分发，须保留版权及许可声明；依赖说明见 [第三方声明](THIRD_PARTY_NOTICES.md)。

[Release v1.1.0](https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks/releases/tag/v1.1.0) · [原生接口](docs/interface.md) · [复现指南](docs/reproduction.md) · [性能测试](docs/performance.md) · [综合结果](docs/synthesis-summary.md)

## 计算与任务单位

一个任务选择一种运算和向量长度 L。K 仅表示 LINCOMB 中参与加权组合的向量数，不是通道数；随包上限为 L ≤ 65,536、K ≤ 8。所有元素都是 Goldilocks 素域 p = 2^64 - 2^32 + 1 下的无符号 64 位规范值，满足 0 ≤ x < p。下表运算均模 p。

| 模式 | 计算与输出单位 |
| --- | --- |
| ADD / SUB / MUL | ADD：y_i = a_i + b_i；SUB：y_i = a_i - b_i；MUL：y_i = a_i * b_i；各返回 L 个输出 |
| FMA | y_i = a_i * b_i + c_i；L 个输出；有限域乘加，不是浮点 FMA |
| LINCOMB | y_i = sum_j lambda_j * a_(j,i)；K 个向量产生 L 个输出，每个向量使用一个任务级固定系数 |
| DOT | sum_i a_i * b_i；一个标量输出，空点积也返回零 |

单通道的一条数据记录提供一次元素运算的操作数。LINCOMB 则先接收 K 条系数记录，再依次接收输出索引 0 的 K 条数据、索引 1 的 K 条数据，以此类推。即使内部元素算术流水化，完整向量任务也不重叠执行。

## 配置速览

| 集成关注点 | 已发布硬件 |
| --- | --- |
| 计算 | Goldilocks 素域上的 ADD、SUB、MUL、FMA、加权 LINCOMB 和 DOT |
| 随包配置 | 向量长度 L 最大 65,536，LINCOMB 项数 K 最大 8，单通道；顶层 `PrimeFieldVector` |
| 表示 | 64 位规范域元素，p = 2^64 - 2^32 + 1 |
| 原生边界 | 任务、系数/数据、带索引的结果及完成 ready/valid 通道 |
| 调度 | 一次执行一个完整向量任务；元素算术流水化，输出采用有界信用 |

## 快速开始

从 [Release v1.1.0](https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks/releases/tag/v1.1.0) 下载源码包，或克隆当前发布快照：

```bash
git clone --branch v1.1.0 --depth 1 https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks.git
cd prime-field-vector-goldilocks
```

按 [工具链说明](docs/toolchain.md) 安装指定版本的依赖，然后在仓库根目录使用新的输出目录运行：

```bash
python3 tools/emit_sv.py --out build/emit --max-l 65536 --max-k 8 --batch 6
```

此生成命令不是完整 RTL 回归。文档规定的验证入口为：

```bash
python3 tools/verify.py --out build/verify --jobs 8
```

预期结果、负控含义及短功能测试与完整性能测试的区别见 [复现指南](docs/reproduction.md)。

## 验证与集成边界

文档所列典型测试包含 104 个任务、5,112,713 条输入记录和 1,966,448 个结果。独立 C/Python 参考、重新生成与随包 SV 的比较、理想输入、协议停顿、复位恢复以及故意修改参考值是分别执行的检查。外层验证命令要求全部正向和负向结果符合预期；负控模拟器返回非零并不直接意味着整体失败。

LINCOMB 先消费系数，再按原生接口约定为每个输出消费连续 K 条数据。DOT 是归约，不是逐元素输出向量；空 DOT 返回零。本模块执行素域算术，不是 NTT、扩域单元或完整约束/证明引擎。外围系统提供数据搬运，并在出错时遵守任务定义的帧排空规则。

### 正确理解性能

理想原生接口下，65,536 元素 MUL 单任务耗时 65,554 周期，实测平均完成间隔为 65,555 周期。模乘有效忙率为每任务 65,536 次有效乘法发射除以 65,555 周期稳态间隔，即 99.9710%；它统计有效算术发射，不是流水线非空比例。DOT 将多个乘积归约成一个结果，输出速率不能与内部乘积处理速率混淆。

上述周期数来自理想原生接口下的 RTL 测量，按 MHz 换算的时间和速率不是板级或硅后实测。Design Compiler 结果只适用于文档所列配置、库角和约束，不代表布局布线签核或系统吞吐率；条件见 [性能测试](docs/performance.md) 和 [综合结果](docs/synthesis-summary.md)。

## 仓库内容

- **Chisel 设计**：[operators/prime-field-vector/src/main/scala/zk/vector/](operators/prime-field-vector/src/main/scala/zk/vector/)，包含任务控制、算术流水线和验证环境；[VectorEnv.scala](operators/prime-field-vector/src/main/scala/zk/vector/VectorEnv.scala) 是验证代码，不属于产品电路。
- **公共域算术**：[GoldilocksArithmetic.scala](common/goldilocks/src/main/scala/zk/field/GoldilocksArithmetic.scala)，提供模加与 Goldilocks 模乘基础结构。
- **生成好的 SV**：[产品 SV 目录](rtl/v1.1.0/l65536-k8-lane1/)，单通道、最大向量长度 65536、最多 8 项线性组合；顶层为 [PrimeFieldVector](rtl/v1.1.0/l65536-k8-lane1/PrimeFieldVector.sv)，[files.f](rtl/v1.1.0/l65536-k8-lane1/files.f) 列出六个产品文件。
- **参考与仿真**：[C 参考模型](operators/prime-field-vector/cmodel/reference.c)、[独立 Python 检查器](operators/prime-field-vector/tools/check_reference.py) 和 [C++ 驱动](operators/prime-field-vector/sim/driver.cpp)，用于参考比对与 RTL 验证。
- **生成与验证工具**：[tools/emit_sv.py](tools/emit_sv.py) 生成电路；[tools/verify.py](tools/verify.py) 验证功能并统计理想接口性能。
- **设计文档**：[硬件架构](docs/architecture.md)、[原生接口](docs/interface.md)、[电路功能与数学模型](docs/mathematical-model.md)、[工具链说明](docs/toolchain.md)、[复现指南](docs/reproduction.md)、[性能测试](docs/performance.md) 和 [综合结果](docs/synthesis-summary.md)。

## 应用场景

当计算在模数 $2^{64}-2^{32}+1$ 的 Goldilocks 域上包含大批量逐元素运算、加权向量组合或点积时，可以把相应计算交给本模块。以下是部分集成位置，不限定某个证明框架：

- **多项式系数或求值向量运算**：向量加减、逐点乘积，以及乘积加上第三个向量。是否需要 NTT、插值或其他变换由目标算法决定，本模块执行对应的域运算。
- **约束值与多项式批量组合**：对多个同长度向量使用固定的一组任务系数做逐点线性组合，例如将已求出的约束值按挑战系数组合。
- **标量归约与点积**：将成对域元素相乘后求和，用于需要这一数学运算的累加或归约步骤。

系统可按目标协议组织数据与任务，并将本模块和变换、哈希或其他算子连接起来；使用其他素域的计算应选择对应的域算术实现。

## 性能指标

理想接口下，长度 65536 的逐元素模乘单任务延迟为 65554 clk，持续任务的平均完成间隔为 65555 clk，模乘有效忙率为 99.9710%；按 500 MHz 换算，结果吞吐率约为 4.99855 亿域元素/s。 详见[性能测试](docs/performance.md)。

## 数学模型

本模块在 $p=2^{64}-2^{32}+1$ 的素域上执行 ADD、SUB、MUL、FMA、LINCOMB 和 DOT 六种确定运算，返回规范域元素以及原任务标签；详细文档给出输入顺序、空向量语义和公式。 详见[电路功能与数学模型](docs/mathematical-model.md)。

## 快速复现

复现流程包括工具准备、Chisel 生成、C/Python 参考核对、功能与协议验证、复位恢复、负对照和周期性能统计，并验证随包 SV 与新生成电路一致。 详见[复现指南](docs/reproduction.md)。

## 综合结果

随包配置在 TSMC 28 nm HPC+ SSG 工艺角、500 MHz 与 10% setup 时钟不确定度约束下无 setup 违例，映射 cell 面积为 0.027502 mm²，99.2229% 的端点最差 setup 路径逻辑级数严格小于 50。 详见[综合结果](docs/synthesis-summary.md)。

## 公开边界

Chisel、生成好的产品 SV、生成脚本、参考模型与功能/性能验证代码均可复现。性能测试采用理想供数与接收时序；集成方可按自己的系统测量外部带宽、存储和共享资源对性能的影响。

很抱歉，由于第三方授权和工程资料保密限制，我们无法提供已有综合流程使用的工艺库、原始 DC Tcl/SDC、原始报告、映射网表或综合快照。[综合结果](docs/synthesis-summary.md) 转述工具、工艺、约束和关键结果，供实现评估参考。

本项目交付独立域向量计算模块，SoC、总线适配、软件驱动、上板流程以及完整证明协议由集成方设计。

## OpenRTL与定制集成

通过[OpenRTL组织主页](https://github.com/OpenRTL-Lab)、[ZK加速器集合](https://github.com/OpenRTL-Lab/zk-accelerators)及[OpenRTL网站](https://openrtl.top/)查找相关模块与合作联系入口。需要定制数据通路、接口或目标平台适配时可联系维护者；MIT商用权不附加购买服务的要求，不要在公开Issue提交保密规格。

## 发布源码与集成

版本标签标识包含来源声明和文档的当前源码树；Release 下载提供本版本硬件源码与随包 RTL。源码包与在线目录的注释及文档可能不同。RTL 集成使用产品文件清单，不把验证环境作为产品电路；支持配置、验证范围和测量条件见验证及综合文档。
