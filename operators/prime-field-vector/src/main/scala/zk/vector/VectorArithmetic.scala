/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Prime-Field Vector Hardware Accelerator
 * Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
 * Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
 * File: operators/prime-field-vector/src/main/scala/zk/vector/VectorArithmetic.scala
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
import zk.field.GL64

/** 块内8位前缀，块间平衡前缀；外部carry仅选择已计算的局部候选。 */
object VectorArithmetic {
  def add(a: UInt, b: UInt, width: Int, ci: Bool = false.B): UInt = {
    val aa = Wire(UInt(width.W)); aa := a
    val bb = Wire(UInt(width.W)); bb := b
    val blocks = (width+7)/8
    val parts = (0 until blocks).map { i =>
      val n = math.min(8,width-8*i)
      val x=aa(8*i+n-1,8*i); val y=bb(8*i+n-1,8*i)
      val zero=GL64.prefix(x,y,n+1)
      val one=GL64.prefix(x,y,n+1,true.B)
      (n,zero,one,(x^y).andR)
    }
    var g=VecInit(parts.map(_._2).zip(parts).map { case (s,p) => s(p._1) }).asUInt
    var p=VecInit(parts.map(_._4)).asUInt
    var d=1
    while(d<blocks) {
      g=g | (p & (g<<d)(blocks-1,0))
      p=p & Cat(p(blocks-d-1,0),((BigInt(1)<<d)-1).U(d.W))
      d*=2
    }
    val result=parts.zipWithIndex.map { case ((n,z,o,_),i) =>
      val carry=if(i==0) ci else g(i-1) | (p(i-1)&ci)
      Mux(carry,o(n-1,0),z(n-1,0))
    }
    Cat(result.reverse)
  }
  def sub(a: UInt,b: UInt,width: Int): UInt = {
    val right=Wire(UInt(width.W)); right:=b
    add(a,~right,width,true.B)
  }
  /** canonical a+b最多2p-2；并行候选sum及sum+delta，进位即sum>=p。 */
  def modAdd(a: UInt,b: UInt): UInt = {
    val x=Cat(0.U(1.W),a); val y=Cat(0.U(1.W),b)
    val delta=((BigInt(1)<<32)-1).U(65.W)
    val compressed=x^y^delta
    val carries=(((x&y)|(x&delta)|(y&delta))<<1)(64,0)
    val adjusted=add(compressed,carries,65)
    val plain=add(x,y,65)
    Mux(adjusted(64),adjusted(63,0),plain(63,0))
  }
}

object VectorMul { val Latency=11 }
class VectorMul extends Module {
  val io=IO(new Bundle { val a=Input(UInt(64.W)); val b=Input(UInt(64.W)); val product=Output(UInt(64.W)) })
  val p=GL64.P.U(66.W)
  val p0=RegNext(io.a(31,0)*io.b(31,0)); val p1=RegNext(io.a(31,0)*io.b(63,32))
  val p2=RegNext(io.a(63,32)*io.b(31,0)); val p3=RegNext(io.a(63,32)*io.b(63,32))
  val outerLow=RegNext(p0); val cross=RegNext(VectorArithmetic.add(p1,p2,65)); val outerHigh=RegNext(p3)
  val low=RegNext(VectorArithmetic.add(outerLow,Cat(cross(31,0),0.U(32.W)),65))
  val crossHigh=RegNext(cross(64,32)); val highDelayed=RegNext(outerHigh)
  val productLow=RegNext(low(63,0))
  val productHigh=RegNext(VectorArithmetic.add(highDelayed,crossHigh,65,low(64)))
  val t0=RegNext(productLow(31,0))
  val highSum=RegNext(VectorArithmetic.add(productLow(63,32),productHigh(31,0),33))
  val subtrahend=RegNext(VectorArithmetic.add(productHigh(31,0),productHigh(63,32),33))
  val positive=RegNext(VectorArithmetic.add(Cat(0.U(1.W),highSum,t0),p,66))
  val delayedSub=RegNext(subtrahend)
  val candidate=RegNext(VectorArithmetic.sub(positive,Cat(0.U(33.W),delayedSub),66))
  val candidateDelay=RegNext(candidate); val candidateMinus=RegNext(VectorArithmetic.sub(candidate,p,66))
  val firstSubtract=RegNext(candidate>=p)
  val once=RegNext(Mux(firstSubtract,candidateMinus,candidateDelay))
  val onceDelay=RegNext(once); val onceMinus=RegNext(VectorArithmetic.sub(once,p,66))
  val secondSubtract=RegNext(once>=p)
  val twice=RegNext(Mux(secondSubtract,onceMinus,onceDelay))
  io.product:=twice(63,0)
}

object VectorAddSub { val Latency=3 }
class VectorAddSub extends Module {
  val io=IO(new Bundle { val a=Input(UInt(64.W)); val b=Input(UInt(64.W)); val subtract=Input(Bool()); val result=Output(UInt(64.W)) })
  val raw=RegNext(Mux(io.subtract,VectorArithmetic.sub(io.a,io.b,65),VectorArithmetic.add(io.a,io.b,65)))
  val sub=RegNext(io.subtract)
  val delayed=RegNext(raw); val subDelayed=RegNext(sub)
  val choose=RegNext(Mux(sub,raw(64),raw>=GL64.P.U(65.W)))
  val plus=RegNext(VectorArithmetic.add(raw,GL64.P.U(65.W),65))
  val minus=RegNext(VectorArithmetic.sub(raw,GL64.P.U(65.W),65))
  io.result:=RegNext(Mux(choose,Mux(subDelayed,plus,minus),delayed)(63,0))
}
