/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Prime-Field Vector Hardware Accelerator
 * Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
 * Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
 * File: operators/prime-field-vector/src/main/scala/zk/vector/PrimeVector.scala
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
import zk.field._

case class VectorConfig(maxL: Int = 65536, maxK: Int = 8, queueDepth: Int = 32) {
  require(maxL>=1 && maxL<=65536 && maxK>=1 && maxK<=8 && queueDepth>=32 && queueDepth<=64)
}
class VectorTask extends Bundle {
  val tag = UInt(32.W); val mode = UInt(3.W); val length = UInt(32.W); val terms = UInt(16.W)
}
class VectorInput extends Bundle {
  val coefficient = Bool(); val a = UInt(64.W); val b = UInt(64.W); val c = UInt(64.W)
  val validCount = UInt(2.W); val last = Bool()
}
class VectorResult extends Bundle {
  val tag = UInt(32.W); val index = UInt(32.W); val value = UInt(64.W)
  val validCount = UInt(2.W); val last = Bool()
}
class VectorCompletion extends Bundle {
  val tag = UInt(32.W); val status = UInt(3.W); val outputCount = UInt(32.W)
}

class PrimeFieldVector(val cfg: VectorConfig) extends Module {
  val io = IO(new Bundle {
    val task = Flipped(Decoupled(new VectorTask))
    val input = Flipped(Decoupled(new VectorInput))
    val result = Decoupled(new VectorResult)
    val completion = Decoupled(new VectorCompletion)
  })
  val sIdle :: sPrepare :: sCoefficients :: sData :: sDrain :: sComplete :: Nil = Enum(6)
  val state = RegInit(sIdle)
  val task = Reg(new VectorTask)
  val badParam = RegInit(false.B); val badFrame = RegInit(false.B); val badField = RegInit(false.B)
  val coefficients = Reg(Vec(cfg.maxK,UInt(64.W)))
  val coefIndex = RegInit(0.U(16.W)); val termIndex = RegInit(0.U(16.W)); val index = RegInit(0.U(32.W))
  val accumulator = RegInit(0.U(64.W))
  val credit = RegInit(0.U(log2Ceil(cfg.queueDepth+1).W))
  val outputCount = RegInit(0.U(32.W))
  val emptyDot = RegInit(false.B)
  val endTerm = Reg(UInt(16.W)); val endIndex = Reg(UInt(32.W))
  val linear = task.mode===4.U; val dot = task.mode===5.U
  val lastTerm = !linear || termIndex===endTerm
  val lastData = lastTerm && index===endIndex
  val makesResult = Mux(dot,lastData,lastTerm)
  io.task.ready := state===sIdle
  io.input.ready := state===sCoefficients || (state===sData && (!makesResult || credit<cfg.queueDepth.U))
  val dataFire = io.input.fire && state===sData
  val reserve = dataFire && makesResult
  val safeA = Mux(io.input.bits.a<GL64.P.U(64.W),io.input.bits.a,0.U)
  val safeB = Mux(io.input.bits.b<GL64.P.U(64.W),io.input.bits.b,0.U)
  val safeC = Mux(io.input.bits.c<GL64.P.U(64.W),io.input.bits.c,0.U)
  val coefBits = math.max(1,log2Ceil(cfg.maxK))
  val rawA=RegNext(io.input.bits.a)
  val rawB=RegNext(Mux(linear,coefficients(termIndex(coefBits-1,0)),io.input.bits.b))
  val rawC=RegNext(io.input.bits.c)
  val inputA=RegNext(Mux(rawA<GL64.P.U,rawA,0.U))
  val inputB=RegNext(Mux(rawB<GL64.P.U,rawB,0.U))
  val inputC=RegNext(Mux(rawC<GL64.P.U,rawC,0.U))
  val inputFire=ShiftRegister(dataFire,2,false.B,true.B)
  val inputIndex=ShiftRegister(index,2)
  val inputFirst=ShiftRegister(Mux(linear,termIndex===0.U,index===0.U),2)
  val inputLastTerm=ShiftRegister(lastTerm,2); val inputLastData=ShiftRegister(lastData,2)
  val mul = Module(new VectorMul)
  val mulFire = inputFire && task.mode>=2.U
  mul.io.a := inputA
  mul.io.b := inputB
  val mulValid = ShiftRegister(mulFire,VectorMul.Latency,false.B,true.B)
  val mulIndex = ShiftRegister(inputIndex,VectorMul.Latency)
  val mulFirst = ShiftRegister(inputFirst,VectorMul.Latency)
  val mulLastTerm = ShiftRegister(inputLastTerm,VectorMul.Latency)
  val mulLastData = ShiftRegister(inputLastData,VectorMul.Latency)
  val delayedC = ShiftRegister(inputC,VectorMul.Latency)
  val adder = Module(new VectorAddSub)
  val fma = task.mode===3.U
  val addFire = (inputFire && task.mode<=1.U) || (mulValid && fma)
  adder.io.a := Mux(fma,mul.io.product,inputA)
  adder.io.b := Mux(fma,delayedC,inputB)
  adder.io.subtract := task.mode===1.U
  val addValid = ShiftRegister(addFire,VectorAddSub.Latency,false.B,true.B)
  val addIndex = ShiftRegister(Mux(fma,mulIndex,inputIndex),VectorAddSub.Latency)
  val reduceFire = mulValid && (linear || dot)
  val sum = Mux(mulFirst,mul.io.product,VectorArithmetic.modAdd(accumulator,mul.io.product))
  when(reduceFire) { accumulator := sum }
  val reduceValid = RegNext(reduceFire && Mux(dot,mulLastData,mulLastTerm),false.B)
  val reduceValue = RegNext(sum)
  val reduceIndex = RegNext(Mux(dot,0.U,mulIndex))
  val queue = Module(new Queue(new VectorResult,cfg.queueDepth))
  val outputIndex = Mux(task.mode<=1.U || fma,addIndex,Mux(task.mode===2.U,mulIndex,reduceIndex))
  queue.io.enq.valid := emptyDot || addValid || (mulValid && task.mode===2.U) || reduceValid
  queue.io.enq.bits.tag := task.tag
  queue.io.enq.bits.index := Mux(emptyDot,0.U,outputIndex)
  queue.io.enq.bits.value := Mux(emptyDot,0.U,Mux(task.mode<=1.U || fma,adder.io.result,
    Mux(task.mode===2.U,mul.io.product,reduceValue)))
  queue.io.enq.bits.validCount := 1.U
  queue.io.enq.bits.last := dot || outputIndex===task.length-1.U
  val outputRegister=Module(new Queue(new VectorResult,1,pipe=true))
  outputRegister.io.enq <> queue.io.deq
  io.result <> outputRegister.io.deq
  when(queue.io.enq.valid) { assert(queue.io.enq.ready,"reserved result queue overflow") }
  when(emptyDot && queue.io.enq.fire) { emptyDot := false.B }
  io.completion.valid := state===sComplete
  io.completion.bits.tag := task.tag
  io.completion.bits.status := Mux(badParam,1.U,Mux(badFrame,2.U,Mux(badField,3.U,0.U)))
  io.completion.bits.outputCount := outputCount

  when(io.task.fire) {
    task := io.task.bits
    state := sPrepare
  }
  when(state===sPrepare) {
    val invalid = task.mode>=6.U || task.length>cfg.maxL.U ||
      Mux(linear,task.terms===0.U || task.terms>cfg.maxK.U,task.terms=/=0.U)
    val empty = dot && task.length===0.U && !invalid
    badParam := invalid; badFrame := false.B; badField := false.B
    coefIndex := 0.U; termIndex := 0.U; index := 0.U; accumulator := 0.U; outputCount := 0.U
    credit := Mux(empty,1.U,0.U); emptyDot := empty
    endIndex := VectorArithmetic.sub(task.length,1.U,32)
    endTerm := VectorArithmetic.sub(task.terms,1.U,16)
    state := Mux(invalid,sComplete,Mux(linear,sCoefficients,
      Mux(task.length===0.U,Mux(empty,sDrain,sComplete),sData)))
  }.otherwise {
    switch(Cat(reserve,io.result.fire)) {
      is("b10".U) { credit := credit+1.U }
      is("b01".U) { credit := credit-1.U }
    }
  }
  when(io.result.fire) {
    assert(credit=/=0.U)
    outputCount := VectorArithmetic.add(outputCount,1.U,32)
  }
  when(io.input.fire) {
    val coeff = state===sCoefficients
    val expectedLast = Mux(coeff,coefIndex===endTerm,lastData)
    when(io.input.bits.coefficient=/=coeff || io.input.bits.last=/=expectedLast || io.input.bits.validCount=/=1.U) {
      badFrame := true.B
    }
    val invalid = io.input.bits.a>=GL64.P.U(64.W) ||
      (!coeff && !linear && io.input.bits.b>=GL64.P.U(64.W)) ||
      (!coeff && fma && io.input.bits.c>=GL64.P.U(64.W))
    when(invalid) { badField := true.B }
    when(coeff) {
      coefficients(coefIndex(coefBits-1,0)) := safeA
      coefIndex := VectorArithmetic.add(coefIndex,1.U,16)
      when(coefIndex===endTerm) { state := Mux(task.length===0.U,sComplete,sData) }
    }.otherwise {
      when(lastTerm) { termIndex := 0.U; index := VectorArithmetic.add(index,1.U,32) }
      .otherwise { termIndex := VectorArithmetic.add(termIndex,1.U,16) }
      when(lastData) { state := sDrain }
    }
  }
  when(state===sDrain && credit===0.U && !queue.io.enq.valid) { state := sComplete }
  when(io.completion.fire) { assert(credit===0.U); state := sIdle }
  assert(credit<=cfg.queueDepth.U)
  // 仿真系统任务进入独立验证层；产品无观测端口或计数器。
  // 首个乘积直接装入累加器，未执行模加，不能计入归约加法忙率。
  val work = Cat(reduceFire && !mulFirst,addFire,mulFire)
  when(work.orR) { printf(p"WORK tag=${task.tag} mask=${work}\n") }
}
