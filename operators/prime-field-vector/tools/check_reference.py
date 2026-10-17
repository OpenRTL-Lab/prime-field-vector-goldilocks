# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks Prime-Field Vector Hardware Accelerator
# Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
# Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
# File: operators/prime-field-vector/tools/check_reference.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""独立Python整数参考，逐字核对C离线结果及固定边界已知答案。"""
import json,sys
from pathlib import Path
P=0xffffffff00000001
def check(directory):
    d=Path(directory); records=iter(int(x,16) for x in (d/'input.hex').open()); outputs=iter(int(x,16) for x in (d/'expected.hex').open()); checked=0
    assert ((P-1)+(P-1))%P==P-2 and (0-(P-1))%P==1
    assert ((P-1)*(P-1))%P==1 and ((P-1)*(P-1)+(P-1))%P==0
    for task in json.loads((d/'tasks.json').read_text()):
        if task['status']==1:continue
        m,l,k=task['mode'],task['length'],task['terms']; lambdas=[];acc=0
        if m==4:
            for _ in range(k):
                v=next(records)&((1<<64)-1);lambdas.append(0 if v>=P else v)
        for _ in range(l):
            linear=0
            for j in range(k if m==4 else 1):
                r=next(records);a=r&((1<<64)-1); b=(r>>64)&((1<<64)-1);c=(r>>128)&((1<<64)-1)
                if a>=P:a=0
                if b>=P:b=0
                if c>=P:c=0
                if m==4:linear=(linear+a*lambdas[j])%P
                elif m==5:acc=(acc+a*b)%P
                else:
                    v=((a+b),(a-b),(a*b),(a*b+c))[m]%P
                    assert next(outputs)==v,(task['tag'],checked); checked+=1
            if m==4:assert next(outputs)==linear;checked+=1
        if m==5:assert next(outputs)==acc;checked+=1
    assert next(records,None) is None and next(outputs,None) is None
    return {'checked_words':checked,'independent_python_pass':True,'fixed_boundary_kat_pass':True}
if __name__=='__main__':print(json.dumps(check(sys.argv[1])))
