/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Prime-Field Vector Hardware Accelerator
 * Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
 * Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
 * File: operators/prime-field-vector/src/main/scala/zk/vector/VectorEnv.scala
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

package zk.vector
import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFileInline

/** 所有供数、时序、检查及性能观测均在ENV；C++不承担协议驱动。 */
class PrimeVectorSimTop(cfg: VectorConfig, directory: String, nt: Int, ni: Int, no: Int) extends Module {
  val io = IO(new Bundle {
    val trigger = Input(Bool()); val testMode = Input(UInt(2.W))
    val done = Output(Bool()); val pass = Output(Bool()); val timedOut = Output(Bool())
    val checked = Output(UInt(32.W)); val mismatches = Output(UInt(32.W)); val cycles = Output(UInt(32.W))
  })
  val running = RegInit(false.B); val done = RegInit(false.B); val timeout = RegInit(false.B)
  val tick = RegInit(0.U(32.W)); val resetDone = RegInit(false.B)
  val abort = running && io.testMode===2.U && tick===100.U && !resetDone
  val dut = withReset(reset.asBool || abort) { Module(new PrimeFieldVector(cfg)) }
  val tasks = Mem(nt,UInt(128.W)); val inputs = Mem(ni,UInt(256.W)); val expected = Mem(no,UInt(64.W))
  loadMemoryFromFileInline(tasks,s"$directory/tasks.hex")
  loadMemoryFromFileInline(inputs,s"$directory/input.hex")
  loadMemoryFromFileInline(expected,s"$directory/expected.hex")
  val issued = RegInit(0.U(32.W)); val completed = RegInit(0.U(32.W)); val active = RegInit(false.B)
  val desc = tasks(issued(log2Ceil(nt)-1,0))
  val current = Reg(UInt(128.W)); val tag = Reg(UInt(32.W))
  val inputCursor = RegInit(0.U(32.W)); val outputCursor = RegInit(0.U(32.W))
  val localIn = RegInit(0.U(32.W)); val localOut = RegInit(0.U(32.W))
  val mismatch = RegInit(0.U(32.W)); val checked = RegInit(0.U(32.W))
  val aTick = Reg(UInt(32.W)); val fTick = RegInit("hffffffff".U(32.W)); val oTick = RegInit("hffffffff".U(32.W))
  val dataFirst = RegInit("hffffffff".U(32.W)); val dataLast = RegInit("hffffffff".U(32.W))
  val offered = RegInit(false.B)
  dut.io.task.valid := running && issued<nt.U && !abort
  dut.io.task.bits.tag := issued; dut.io.task.bits.mode := desc(2,0)
  dut.io.task.bits.length := desc(34,3); dut.io.task.bits.terms := desc(50,35)
  val record = inputs(inputCursor(math.max(1,log2Ceil(ni))-1,0))
  val pause = io.testMode===1.U && (tick%13.U)<3.U
  dut.io.input.valid := running && active && localIn<current(82,51) && (offered || !pause) && !abort
  dut.io.input.bits.a := record(63,0); dut.io.input.bits.b := record(127,64); dut.io.input.bits.c := record(191,128)
  dut.io.input.bits.coefficient := record(192); dut.io.input.bits.last := record(193); dut.io.input.bits.validCount := record(195,194)
  dut.io.result.ready := running && !(io.testMode===1.U && tick%97.U<47.U) && !abort
  dut.io.completion.ready := running && !(io.testMode===1.U && tick%11.U<5.U) && !abort
  val ref = expected(outputCursor(math.max(1,log2Ceil(no))-1,0))
  val expectValue = ref ^ Mux(io.testMode===3.U && outputCursor===0.U,1.U(64.W),0.U(64.W))
  val outputError = dut.io.result.fire && (
    !active || dut.io.result.bits.tag=/=tag || dut.io.result.bits.index=/=localOut ||
    dut.io.result.bits.validCount=/=1.U || dut.io.result.bits.last=/=(localOut===current(114,83)-1.U) ||
    dut.io.result.bits.value=/=expectValue || localOut>=current(114,83))
  val completionError = dut.io.completion.fire && (
    !active || dut.io.completion.bits.tag=/=tag || dut.io.completion.bits.status=/=current(118,115) ||
    dut.io.completion.bits.outputCount=/=localOut || localOut=/=current(114,83) || localIn=/=current(82,51))
  when(outputError || completionError) { mismatch := mismatch+PopCount(Seq(outputError,completionError)) }
  when(dut.io.input.valid && !dut.io.input.ready) { offered := true.B }
  when(dut.io.input.fire) {
    offered := false.B; inputCursor := inputCursor+1.U; localIn := localIn+1.U
    when(localIn===0.U) { fTick := tick }
    when(!record(192)) { when(dataFirst==="hffffffff".U) { dataFirst := tick }; dataLast := tick }
  }
  when(dut.io.task.fire) {
    current := desc; tag := issued; issued := issued+1.U; active := true.B
    localIn := 0.U; localOut := 0.U; aTick := tick
    fTick := "hffffffff".U; oTick := "hffffffff".U
    dataFirst := "hffffffff".U; dataLast := "hffffffff".U
  }
  when(dut.io.result.fire) { checked := checked+1.U; outputCursor := outputCursor+1.U; localOut := localOut+1.U; oTick := tick }
  when(dut.io.completion.fire) {
    active := false.B; completed := completed+1.U
    printf(p"FRAME tag=${tag} mode=${current(2,0)} A=${aTick} F=${fTick} O=${oTick} C=${tick} DF=${dataFirst} DL=${dataLast}\n")
    when(completed===(nt-1).U) { running := false.B; done := true.B }
  }
  // 任意长度的输出/完成背压均须保持payload；异常帧也不绕过检查。
  val resultHeld = RegNext(dut.io.result.valid && !dut.io.result.ready,false.B)
  val resultBits = RegNext(dut.io.result.bits.asUInt)
  val completionHeld = RegNext(dut.io.completion.valid && !dut.io.completion.ready,false.B)
  val completionBits = RegNext(dut.io.completion.bits.asUInt)
  when(!abort && !RegNext(abort,false.B)) {
    when(resultHeld) { assert(dut.io.result.valid && dut.io.result.bits.asUInt===resultBits) }
    when(completionHeld) { assert(dut.io.completion.valid && dut.io.completion.bits.asUInt===completionBits) }
  }
  when(io.trigger && !running && !done) { running := true.B }
  when(running) { tick := tick+1.U }
  when(running && tick>=20000000.U) { timeout := true.B; running := false.B; done := true.B }
  when(abort) {
    resetDone := true.B; issued := 0.U; completed := 0.U; active := false.B; offered := false.B
    inputCursor := 0.U; outputCursor := 0.U; mismatch := 0.U; checked := 0.U; localIn := 0.U; localOut := 0.U
  }
  io.done := done; io.pass := done && !timeout && mismatch===0.U && completed===nt.U
  io.timedOut := timeout; io.checked := checked; io.mismatches := mismatch; io.cycles := tick
}
