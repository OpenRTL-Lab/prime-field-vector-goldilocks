#!/usr/bin/env python3
# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks Prime-Field Vector Hardware Accelerator
# Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
# Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
# File: tools/audit_public.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""检查公开载荷文件、文档链接、语言配对和常见敏感信息。"""
import argparse
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BASE = {'README.md', 'README.zh-CN.md', 'THIRD_PARTY_NOTICES.md',
        'THIRD_PARTY_NOTICES.en.md', 'LICENSE', 'VERSION', '.gitignore', 'build.mill'}
DOCS = ('architecture', 'interface', 'mathematical-model', 'toolchain',
        'reproduction', 'performance', 'synthesis-summary', 'publication-check')
SOURCES = {
    'common/goldilocks/src/main/scala/zk/field/GoldilocksArithmetic.scala',
    'operators/prime-field-vector/src/main/scala/zk/vector/PrimeVector.scala',
    'operators/prime-field-vector/src/main/scala/zk/vector/VectorArithmetic.scala',
    'operators/prime-field-vector/src/main/scala/zk/vector/VectorEnv.scala',
    'operators/prime-field-vector/src/tool/scala/zk/vector/Emit.scala',
    'operators/prime-field-vector/cmodel/reference.c',
    'operators/prime-field-vector/sim/driver.cpp',
    'operators/prime-field-vector/tools/check_reference.py',
    'tools/emit_sv.py', 'tools/verify.py', 'tools/audit_public.py'}
RTL = 'rtl/v1.1.0/l65536-k8-lane1/'
MODULES = ('PrimeFieldVector', 'Queue1_VectorResult', 'Queue32_VectorResult',
           'VectorAddSub', 'VectorMul', 'ram_32x131')
EXPECTED = BASE | SOURCES | {f'docs/{n}{s}.md' for n in DOCS for s in ('', '.en')}
EXPECTED |= {RTL + n + '.sv' for n in MODULES} | {RTL + 'files.f'}
EXPECTED |= {'docs/journal.md', 'rtl/v1.1.0/l65536-k8-lane1/files.f.license', 'docs/source-headers.en.md', 'docs/journal.en.md', 'tools/source_headers.py', 'tools/test_source_headers.py', 'docs/source-headers.md', 'VERSION.license'}
EXCLUDED = {'.git', '.private', 'build', 'out', '__pycache__', '.bsp', '.bloop', '.metals'}


def public_files(root=ROOT):
    result = []
    for path in sorted(root.iterdir()):
        if path.name in EXCLUDED or path.name.startswith('.mill-'):
            continue
        if path.is_symlink():
            raise ValueError('Symlink in public tree')
        if path.is_dir():
            for child in sorted(path.rglob('*')):
                rel = child.relative_to(root)
                if any(part in EXCLUDED for part in rel.parts):
                    continue
                if child.is_symlink():
                    raise ValueError('Symlink in public tree')
                if child.is_file():
                    result.append(rel.as_posix())
        elif path.is_file():
            result.append(path.relative_to(root).as_posix())
    return sorted(result)


PATTERNS = {
    'machine-path': r'\x2f(?:home|root|mnt|opt|tmp|var|Users|media|work|workspace|private|[A-Za-z0-9_.-]+-(?:data|worktrees))\x2f[^\s"\x27]+',
    'credential': r'\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\b',
    'private-key': r'-----BEGIN (?:[A-Z ]+ )?PRIVATE KEY-----',
    'internal-identifier': r'(?<![A-Za-z0-9])[a-fA-F0-9]{40,}(?![A-Za-z0-9])',
    'host-address': r'\b(?:10\.\d{1,3}\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}|127\.0\.0\.1)\b'}
PATTERNS['windows-path'] = r'(?<![A-Za-z0-9])(?:[A-Za-z]:[\\/]|\\\\[A-Za-z0-9_-]+[\\/])'


def sensitive(text):
    return [kind for kind, pattern in PATTERNS.items() if re.search(pattern, text)]


def audit(root=ROOT):
    files = public_files(root)
    if set(files) != EXPECTED:
        raise ValueError('Unexpected or missing public file: ' + str(sorted(set(files) ^ EXPECTED)))
    labels = {}
    for name in files:
        text = (root / name).read_text(encoding='utf-8')
        issues = sensitive(text)
        if issues:
            raise ValueError(name + ': ' + ','.join(issues))
        if name.endswith('.md'):
            english = name == 'README.md' or name.endswith('.en.md')
            counterpart = ('README.zh-CN.md' if name == 'README.md' else 'README.md' if name == 'README.zh-CN.md'
                           else name[:-6] + '.md' if name.endswith('.en.md') else name[:-3] + '.en.md')
            if counterpart not in files:
                raise ValueError('Missing translation: ' + name)
            paragraphs = re.sub(r'\A<!--\nOpenRTL-Header-Begin.*?-->\n\n', '', text, count=1, flags=re.S).split('\n\n')
            if len(paragraphs) < 2 or not paragraphs[1].strip() or paragraphs[1].startswith(('#', '|', '-')):
                raise ValueError('Missing opening summary: ' + name)
            if '\\operatorname' in text:
                raise ValueError('Unsupported math macro: ' + name)
            for label, target in re.findall(r'\[([^\]]+)\]\(([^)]+)\)', text):
                if target.startswith(('https://', 'http://', '#')):
                    continue
                dest = ((root / name).parent / target.split('#', 1)[0]).resolve()
                if not dest.is_relative_to(root.resolve()) or not dest.exists():
                    raise ValueError('Invalid link: ' + name + ' -> ' + target)
                # Language switches have intentionally language-specific labels.
                switch = (label == ('简体中文' if english else 'English')
                          and dest == (root / counterpart).resolve())
                if switch:
                    continue
                target_english = dest.name == 'README.md' or dest.name.endswith('.en.md')
                if english and dest.suffix == '.md' and not target_english:
                    raise ValueError('English body links to Chinese document: ' + name)
                language = 'en' if english else 'zh'
                key = (language, dest)
                if key in labels and labels[key] != label:
                    raise ValueError('Inconsistent link label: ' + name + ' -> ' + target)
                labels[key] = label
    lines = (root / RTL / 'files.f').read_text().splitlines()
    if set(lines) != {RTL + n + '.sv' for n in MODULES}:
        raise ValueError('Incorrect product file list')
    return files


def controls():
    bad = ['/' + 'home' + '/someone/file', 'gh' + 'p_' + 'X' * 36,
           '-' * 5 + 'BEGIN PRIVATE KEY' + '-' * 5, 'a' * 40,
           '10.' + '1.2.3']
    if not all(sensitive(x) for x in bad) or sensitive('p=0xffffffff00000001; 500 MHz'):
        raise ValueError('Audit controls failed')


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--self-test', action='store_true')
    args = p.parse_args()
    if args.self_test:
        controls()
    files = audit()
    print(f'Public payload checks passed: {len(files)} files; document links and sensitive-content checks passed.')
