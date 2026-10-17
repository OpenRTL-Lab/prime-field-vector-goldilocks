<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks Prime-Field Vector Hardware Accelerator
Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
File: docs/reproduction.md

SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 复现指南

[English](reproduction.en.md)

本文件给出从工具准备到电路生成、独立参考核对、产品 lint、四类 RTL 验证与性能测量的完整步骤；默认同时验证重新生成的电路与随包 SV，输出可阅读的周期结果。

## 获取版本快照

[Release v1.1.0](https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks/releases/tag/v1.1.0)

```bash
git clone --branch v1.1.0 --depth 1 https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks.git
cd prime-field-vector-goldilocks
```

版本标签标识包含来源声明和文档的当前源码树；Release 下载提供本版本硬件源码与随包 RTL。源码包与在线目录的注释及文档可能不同。RTL 集成使用产品文件清单，不把验证环境作为产品电路；支持配置、验证范围和测量条件见验证及综合文档。

## 1. 准备环境

按 [工具链说明](toolchain.md) 安装开源工具并配置 PATH。所有以下命令在仓库根目录执行。脚本输出必须是 `build/` 下尚不存在的相对目录；重复测试时选择新的名称，以保留每次日志。

先确认工具能够调用，再开始生成或验证：

```bash
java -version
mill --version
python3 --version
verilator --version
cc --version
g++ --version
```

`--max-l` 设置最大向量长度，`--max-k` 设置 LINCOMB 最大项数；`--batch`（6..8，默认6）设置每种算术模式的满长度性能任务数，完整测试还包含边界和错误任务。`--jobs` 仅控制编译并行度，不是硬件通道数或任务并发数。

## 2. 完整复现

以下命令编译 C 参考、生成测试向量、用独立 Python 整数模型核对参考、从 Chisel 生成 SV，随后执行产品 lint 与 ideal/protocol/reset/negative 四类测试，统计周期性能，并检查随包产品与新生成产品的代码及仿真结果一致：

```bash
python3 tools/verify.py --out build/verify --jobs 8
```

典型配置为最大长度 65536、最大项数 8、结果队列深度 32；测试共 104 个任务、5,112,713 条输入记录、1,966,448 个结果。正常终止码为 0。

主要输出：

| 输出（以 build/verify 为例） | 内容 |
| --- | --- |
| configuration.json | 参数和向量数量 |
| results.json | 验证结论、各模式延迟、吞吐率、忙率 |
| vectors/tasks.json | 每个任务的模式、长度、项数及 benchmark 标记 |
| rtl/product.f | 生成的纯产品文件清单 |
| generated/ideal.log | 新生成产品的任务时间戳与有效工作记录 |
| bundled/ideal.log | 随包产品的相同测量 |
| generated/ 与 bundled/ 下其他日志 | 编译、协议、复位与负对照 |

`results.json` 的 `status=0` 表示全部检查通过。ideal、protocol、reset 的 `mismatches=0, timedout=0, pass=1`；negative 故意修改第一个参考结果，要求且仅允许 `mismatches=1, pass=0`。负对照仿真退出码 1 是验证脚本预期的成功检测，整条验证命令仍退出 0。

## 3. 只生成电路

以下命令生成典型配置的产品电路与单独验证环境，并准备经过 C/Python 核对的测试向量；它不启动 RTL 仿真：

```bash
python3 tools/emit_sv.py --out build/emit --max-l 65536 --max-k 8 --batch 6
```

集成时只使用 `build/emit/rtl/product.f` 或随包 [files.f](../rtl/v1.1.0/l65536-k8-lane1/files.f) 的文件，不使用验证顶层 `PrimeVectorSimTop` 或仿真初始化存储。

以下命令单独对随包产品做 lint，确认顶层及依赖可解析：

```bash
verilator --lint-only --assert --top-module PrimeFieldVector -f rtl/v1.1.0/l65536-k8-lane1/files.f
```

## 4. 检查其他尺寸

以下命令生成并验证最大长度 17、最多 4 项线性组合的小配置，使用同样的数学与协议检查：

```bash
python3 tools/verify.py --out build/verify-small --max-l 17 --max-k 4 --jobs 8
```

生成工具支持 `--max-l 1..65536`、`--max-k 1..8`。随包 SV 与典型配置的综合结果不变；其他尺寸的生成与功能性能可按相同流程自行测量。

## 5. 阅读性能结果

`performance.modes` 按 0..5 对应六种模式，包含 `latency_cycles`、`task_interval_cycles`、`tasks_per_cycle`、`output_words_per_cycle`、`unit_issues_per_task` 和 `unit_busy_saturated_percent`。详细测量口径与测量结果见 [性能测试](performance.md)。

仿真日志时间戳来自 RTL 时钟，不是程序运行的墙钟时间。理想模式用于性能测试；协议背压模式用于验证稳健性，不用于给理想性能加入外部约束。
