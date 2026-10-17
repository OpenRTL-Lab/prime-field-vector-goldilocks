#!/usr/bin/env python3
# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks Prime-Field Vector Hardware Accelerator
# Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
# Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
# File: tools/emit_sv.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""生成参考向量、独立产品SV及Chisel验证环境；不需要Git或专有综合工具。"""
from source_headers import stamp_generated
import argparse
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
OP = Path('operators/prime-field-vector')
BUNDLED = Path('rtl/v1.1.0/l65536-k8-lane1')


def run(command, out, label, expected=0, env=None):
    with (out / (label + '.log')).open('x') as log:
        result = subprocess.run([str(x) for x in command], cwd=ROOT,
                                stdout=log, stderr=subprocess.STDOUT, env=env)
    if result.returncode != expected:
        raise RuntimeError(f'{label}: exit={result.returncode}, expected={expected}; inspect its log')


def code(text):
    return re.sub(r'/\*.*?\*/|//[^\n]*', '', text, flags=re.S)


def product_closure(rtl):
    stamp_generated(rtl)
    definitions, texts = {}, {}
    for path in sorted(rtl.glob('*.sv')):
        text = re.sub(r'[ \t]*//[^\n]*\.scala:\d+:\d+[^\n]*', '', path.read_text())
        path.write_text(text)
        texts[path] = code(text)
        for name in re.findall(r'\bmodule\s+(\w+)', texts[path]):
            if name in definitions:
                raise ValueError('Duplicate module: ' + name)
            definitions[name] = path
    selected, pending = set(), ['PrimeFieldVector']
    while pending:
        path = definitions[pending.pop()]
        if path in selected:
            continue
        selected.add(path)
        instances = set(re.findall(r'^[ \t]*(\w+)\b[ \t]+(?:#\s*\([^;]*?\)\s*)?\w+\s*\(',
                                   texts[path], re.M | re.S))
        pending.extend(instances & definitions.keys())
    for path in selected:
        stripped = re.sub(r'"(?:[^"\\]|\\.)*"', '""', texts[path])
        if re.search(r'\$readmem|%|\b(?:DSP48\w*|RAMB\w*|URAM\w*|XPM\w*)\b', stripped):
            raise ValueError('Unexpected product dependency: ' + path.name)
        if 'SimTop' in stripped:
            raise ValueError('Verification environment in product')
    return sorted(selected)


def simulation_list(rtl, target, bundled=False):
    sources = sorted(rtl.rglob('*.sv'))
    if bundled:
        generated_product = set(product_closure(rtl))
        sources = [p for p in sources if p not in generated_product]
        sources += sorted(BUNDLED.glob('*.sv'))
    includes = sorted({p.parent for p in sources})
    target.write_text(''.join('+incdir+' + str(p) + '\n' for p in includes)
                      + ''.join(str(p) + '\n' for p in sources))


def generate(out, max_l=65536, max_k=8, batch=6):
    os.chdir(ROOT)
    out = Path(out)
    if out.is_absolute() or '..' in out.parts or not out.parts or out.parts[0] != 'build':
        raise ValueError('Output must be a relative directory inside build')
    if out.exists():
        raise ValueError('Output exists; choose a new directory')
    if not (1 <= max_l <= 65536 and 1 <= max_k <= 8 and 6 <= batch <= 8):
        raise ValueError('max-l: 1..65536; max-k: 1..8; batch: 6..8')
    out.mkdir(parents=True)
    vectors = out / 'vectors'
    vectors.mkdir()
    run([os.environ.get('CC', 'cc'), '-std=c11', '-O2', '-Wall', '-Wextra', '-Werror',
         OP / 'cmodel/reference.c', '-o', out / 'reference'], out, 'c-compile')
    run([out / 'reference', vectors, max_l, max_k, batch], out, 'c-reference')
    sizes = json.loads((out / 'c-reference.log').read_text())
    run([sys.executable, OP / 'tools/check_reference.py', vectors], out, 'python-reference')
    rtl = out / 'rtl'
    run(shlex.split(os.environ.get('MILL', 'mill')) + ['-i', 'primeVector.tool.runMain',
         'zk.vector.Emit', rtl, max_l, max_k, vectors, sizes['tasks'], sizes['inputs'], sizes['outputs']], out, 'emit')
    product = product_closure(rtl)
    (rtl / 'product.f').write_text(''.join(str(p) + '\n' for p in product))
    simulation_list(rtl, rtl / 'simulation.f')
    (out / 'configuration.json').write_text(json.dumps(
        {'version': '1.1.0', 'max_l': max_l, 'max_k': max_k, 'batch': batch,
         'queue_depth': 32, 'vectors': sizes}, indent=2) + '\n')
    return out, rtl, sizes


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', default='build/emit')
    p.add_argument('--max-l', type=int, default=65536)
    p.add_argument('--max-k', type=int, default=8)
    p.add_argument('--batch', type=int, default=6)
    a = p.parse_args()
    generate(a.out, a.max_l, a.max_k, a.batch)
    print('Generation passed; product list: <out>/rtl/product.f')
