/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Prime-Field Vector Hardware Accelerator
 * Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
 * Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
 * File: common/goldilocks/src/main/scala/zk/field/GoldilocksArithmetic.scala
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

package zk.field
import chisel3._
import chisel3.util._

/** 复用NTT v1.1.0前缀算术结构，独立公共包；不修改旧产品或使用FPGA原语。 */
object GL64 {
  val P: BigInt = BigInt("ffffffff00000001", 16)
  def prefix(a: UInt, b: UInt, width: Int, carryIn: Bool = false.B): UInt = {
    val l = Wire(UInt(width.W)); l := a
    val r = Wire(UInt(width.W)); r := b
    val bits = l ^ r
    var propagate = bits
    var generate = l & r
    var distance = 1
    while(distance < width) {
      val shiftedG = (generate << distance)(width-1,0)
      val shiftedP = Cat(propagate(width-distance-1,0), ((BigInt(1)<<distance)-1).U(distance.W))
      generate = generate | (propagate & shiftedG)
      propagate = propagate & shiftedP
      distance *= 2
    }
    val carries = ((generate << 1) | ((propagate << 1) & Fill(width+1,carryIn)) | carryIn.asUInt)(width-1,0)
    bits ^ carries
  }
  def subtract(a: UInt, b: UInt, width: Int): UInt = {
    val right = Wire(UInt(width.W)); right := b
    prefix(a, ~right, width, true.B)
  }
  def add(a: UInt, b: UInt): UInt = {
    val sum = prefix(a,b,65)
    Mux(sum >= P.U(65.W), subtract(sum,P.U(65.W),65), sum)(63,0)
  }
}

object GoldilocksMul { val Latency = 11 }
class GoldilocksMul extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(64.W)); val b = Input(UInt(64.W)); val product = Output(UInt(64.W))
  })
  private val p = GL64.P.U(66.W)
  private val p0 = RegNext(io.a(31,0)*io.b(31,0))
  private val p1 = RegNext(io.a(31,0)*io.b(63,32))
  private val p2 = RegNext(io.a(63,32)*io.b(31,0))
  private val p3 = RegNext(io.a(63,32)*io.b(63,32))
  private val outerLow = RegNext(p0)
  private val cross = RegNext(GL64.prefix(p1,p2,65))
  private val outerHigh = RegNext(p3)
  private val low = RegNext(GL64.prefix(outerLow,Cat(cross(31,0),0.U(32.W)),65))
  private val crossHigh = RegNext(cross(64,32))
  private val highDelayed = RegNext(outerHigh)
  private val productLow = RegNext(low(63,0))
  private val productHigh = RegNext(GL64.prefix(Cat(0.U(1.W),highDelayed),Cat(0.U(32.W),crossHigh),65,low(64)))
  private val t0 = RegNext(productLow(31,0))
  private val highSum = RegNext(GL64.prefix(productLow(63,32),productHigh(31,0),33))
  private val subtrahend = RegNext(GL64.prefix(productHigh(31,0),productHigh(63,32),33))
  private val positive = RegNext(GL64.prefix(Cat(0.U(1.W),highSum,t0),p,66))
  private val delayedSub = RegNext(subtrahend)
  private val candidate = RegNext(GL64.subtract(positive,Cat(0.U(33.W),delayedSub),66))
  private val candidateDelay = RegNext(candidate)
  private val firstSubtract = RegNext(candidate>=p)
  private val once = RegNext(Mux(firstSubtract,GL64.subtract(candidateDelay,p,66),candidateDelay))
  private val onceDelay = RegNext(once)
  private val secondSubtract = RegNext(once>=p)
  private val twice = RegNext(Mux(secondSubtract,GL64.subtract(onceDelay,p,66),onceDelay))
  io.product := twice(63,0)
}

object GoldilocksAddSub { val Latency = 3 }
class GoldilocksAddSub extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(64.W)); val b = Input(UInt(64.W)); val subtract = Input(Bool())
    val result = Output(UInt(64.W))
  })
  val raw = RegNext(Mux(io.subtract,GL64.subtract(io.a,io.b,65),GL64.prefix(io.a,io.b,65)))
  val sub = RegNext(io.subtract)
  val delayed = RegNext(raw)
  val choose = RegNext(Mux(sub,raw(64),raw>=GL64.P.U(65.W)))
  val adjusted = RegNext(Mux(sub,GL64.prefix(raw,GL64.P.U(65.W),65),GL64.subtract(raw,GL64.P.U(65.W),65)))
  io.result := RegNext(Mux(choose,adjusted,delayed)(63,0))
}
