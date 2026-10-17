#!/usr/bin/env python3
# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks Prime-Field Vector Hardware Accelerator
# Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
# Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
# File: tools/verify.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""公开副本生成、参考比对、RTL仿真与周期性能统计。"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
from emit_sv import ROOT, OP, BUNDLED, code, generate, product_closure, run, simulation_list


def metrics(out, tasks):
    frames, counts = {}, {}
    for line in (out / 'ideal.log').open():
        match = re.search(r'WORK tag=\s*(\d+) mask=\s*(\d+)', line)
        if match:
            tag, mask = map(int, match.groups())
            values = counts.setdefault(tag, [0, 0, 0])
            for i in range(3):
                if mask & (1 << i):
                    values[i] += 1
        match = re.search(r'FRAME tag=\s*(\d+) mode=\s*(\d+) A=\s*(\d+) F=\s*(\d+) O=\s*(\d+) C=\s*(\d+) DF=\s*(\d+) DL=\s*(\d+)', line)
        if match:
            values = list(map(int, match.groups()))
            frames[values[0]] = dict(zip(('tag', 'mode', 'A', 'F', 'O', 'C', 'DF', 'DL'), values))
    if len(frames) != len(tasks):
        raise ValueError('Missing task frames')
    result = {}
    for mode in range(6):
        group = [t for t in tasks if t['benchmark'] and t['mode'] == mode]
        if len(group) < 6:
            raise ValueError('Insufficient throughput samples')
        sample = frames[group[2]['tag']]
        length, k = group[2]['length'], group[2]['terms']
        n = length * (k if mode == 4 else 1)
        adds = length * (k - 1) if mode == 4 else length - 1 if mode == 5 else 0
        expected = [n if mode >= 2 else 0, n if mode in (0, 1, 3) else 0, adds]
        for t in group:
            if counts.get(t['tag'], [0, 0, 0]) != expected:
                raise ValueError('Incorrect useful issue count')
        first, last = frames[group[1]['tag']], frames[group[-2]['tag']]
        intervals, window = len(group) - 3, last['C'] - first['C']
        latency = sample['C'] - sample['A']
        result[str(mode)] = {
            'length': length, 'terms': k, 'tasks': len(group),
            'latency_cycles': latency, 'last_output_latency_cycles': sample['O'] - sample['A'],
            'task_interval_cycles': window / intervals, 'tasks_per_cycle': intervals / window,
            'output_words_per_cycle': group[2]['output_count'] * intervals / window,
            'input_data_span_cycles': sample['DL'] - sample['DF'] + 1,
            'unit_issues_per_task': expected,
            'unit_busy_saturated_percent': [100 * x * intervals / window for x in expected],
            'unit_idle_task_cycles': [latency - x for x in expected],
            'window_first_completion': first['C'], 'window_last_completion': last['C'],
            'window_intervals': intervals}
    return {'unit_names': ['mod_mul', 'mod_add_sub', 'mod_reduce_feedback'], 'modes': result}


def build_and_test(out, rtl, sizes, jobs, bundled=False):
    name = 'bundled' if bundled else 'generated'
    target = out / name
    target.mkdir()
    simulation_list(rtl, target / 'simulation.f', bundled)
    obj = target / 'obj'
    env = dict(os.environ, CCACHE_DISABLE='1')
    run(['verilator', '--cc', '--exe', '--build', '--assert', '-O3', '-j', jobs,
         '-DENABLE_INITIAL_MEM_', '--top-module', 'PrimeVectorSimTop', '--Mdir', obj,
         '-f', target / 'simulation.f', (ROOT / OP / 'sim/driver.cpp')], target, 'compile', env=env)
    results = {}
    for mode, label in enumerate(('ideal', 'protocol', 'reset', 'negative')):
        run([obj / 'VPrimeVectorSimTop', mode], target, label, 1 if mode == 3 else 0)
        m = re.search(r'RESULT pass=(\d+) checked=(\d+) mismatches=(\d+) cycles=(\d+) timedout=(\d+)',
                      (target / (label + '.log')).read_text())
        if not m:
            raise ValueError('Simulation result missing')
        values = dict(zip(('pass', 'checked', 'mismatches', 'cycles', 'timedout'), map(int, m.groups())))
        if (values['checked'] != sizes['outputs'] or values['timedout'] != 0
                or values['mismatches'] != (1 if mode == 3 else 0)
                or values['pass'] != (0 if mode == 3 else 1)):
            raise ValueError('Incorrect verification verdict')
        results[label] = values
    return results, metrics(target, json.loads((out / 'vectors/tasks.json').read_text()))


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--out', default='build/verify')
    p.add_argument('--max-l', type=int, default=65536)
    p.add_argument('--max-k', type=int, default=8)
    p.add_argument('--jobs', type=int, default=8)
    a = p.parse_args()
    if not 1 <= a.jobs <= 64:
        p.error('jobs must be 1..64')
    out, rtl, sizes = generate(a.out, a.max_l, a.max_k)
    report = {'status': 1, 'ideal_interfaces': True, 'vectors': sizes, 'max_l': a.max_l, 'max_k': a.max_k}
    try:
        run(['verilator', '--lint-only', '--assert', '--top-module', 'PrimeFieldVector',
             '-f', rtl / 'product.f'], out, 'lint-generated')
        if (a.max_l, a.max_k) == (65536, 8):
            generated = {p.name: code(p.read_text()) for p in product_closure(rtl)}
            supplied = {p.name: code(p.read_text()) for p in BUNDLED.glob('*.sv')}
            if generated != supplied:
                raise ValueError('Bundled and regenerated products differ')
            report['bundled_circuit_equal'] = True
            run(['verilator', '--lint-only', '--assert', '--top-module', 'PrimeFieldVector',
                 '-f', BUNDLED / 'files.f'], out, 'lint-bundled')
        tests, performance = build_and_test(out, rtl, sizes, a.jobs)
        report['generated_tests'], report['performance'] = tests, performance
        if report.get('bundled_circuit_equal'):
            tests2, performance2 = build_and_test(out, rtl, sizes, a.jobs, True)
            if tests != tests2 or performance != performance2:
                raise ValueError('Bundled and regenerated simulation measurements differ')
            report['bundled_tests'] = tests2
        report['status'] = 0
    finally:
        pending = out / 'results.pending'
        pending.write_text(json.dumps(report, indent=2) + '\n')
        pending.replace(out / 'results.json')
    print('Verification passed; cycle metrics: <out>/results.json')


if __name__ == '__main__':
    main()
