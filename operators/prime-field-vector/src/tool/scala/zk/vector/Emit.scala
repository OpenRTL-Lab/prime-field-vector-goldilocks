/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks Prime-Field Vector Hardware Accelerator
 * Chisel hardware for vector arithmetic and dot products over the Goldilocks field.
 * Repository: https://github.com/OpenRTL-Lab/prime-field-vector-goldilocks
 * File: operators/prime-field-vector/src/tool/scala/zk/vector/Emit.scala
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks Prime Field Vector contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

package zk.vector
import circt.stage.ChiselStage
import java.nio.file.{Files,Paths}
object Emit extends App {
  require(args.length==7,"Emit output maxL maxK vectors tasks inputs outputs")
  val target=Paths.get(args(0)).normalize()
  require(!target.isAbsolute && target.startsWith(Paths.get("build")) && !Files.exists(target))
  Files.createDirectories(target)
  ChiselStage.emitSystemVerilog(new PrimeVectorSimTop(VectorConfig(args(1).toInt,args(2).toInt),args(3),args(4).toInt,args(5).toInt,args(6).toInt),
    firtoolOpts=Array("-O=release","-disable-all-randomization","--lowering-options=disallowLocalVariables","-split-verilog",s"-o=$target"))
}
