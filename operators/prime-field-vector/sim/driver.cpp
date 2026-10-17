/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Prime-Field Vector Hardware Accelerator
 * Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
 * Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
 * File: operators/prime-field-vector/sim/driver.cpp
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

#include "VPrimeVectorSimTop.h"
#include "verilated.h"
#include <cstdio>
#include <cstdlib>
#include <memory>
int main(int argc,char **argv){
  auto ctx=std::make_unique<VerilatedContext>();ctx->commandArgs(argc,argv);
  auto top=std::make_unique<VPrimeVectorSimTop>(ctx.get());
  top->io_testMode=argc>1?std::atoi(argv[1]):0;top->io_trigger=0;
  auto step=[&](){top->clock=0;top->eval();ctx->timeInc(1);top->clock=1;top->eval();ctx->timeInc(1);};
  top->reset=1;for(int i=0;i<8;i++)step();top->reset=0;step();top->io_trigger=1;step();top->io_trigger=0;
  for(unsigned n=0;n<20000100&&!top->io_done;n++)step();
  std::printf("RESULT pass=%u checked=%u mismatches=%u cycles=%u timedout=%u\n",top->io_pass,top->io_checked,top->io_mismatches,top->io_cycles,top->io_timedOut);
  int status=top->io_done&&top->io_pass?0:1;top->final();return status;
}
