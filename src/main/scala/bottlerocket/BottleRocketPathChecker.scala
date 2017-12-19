// Copyright 2017 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Generator that uses FIRRTL to check for long bus-to-bus
// combinational paths in BottleRocket

package bottlerocket

import chisel3._
import firrtl.{ExecutionOptionsManager, HasFirrtlOptions, CommonOptions, FirrtlExecutionOptions, ComposableOptions}
import freechips.rocketchip._
import rocket._
import devices.debug.DMIIO
import Params._

object BottleRocketPathChecker extends App {
  val config = new DefaultBottleRocketConfig

  trait HasBROptions {
    self: ExecutionOptionsManager =>
    var brOptions = BROptions()
    parser.note("BottleRocket options")
    parser.opt[Int]("nProgInterrupts")
      .abbr("nInts")
      .valueName("<nInts>")
      .foreach { n => brOptions = brOptions.copy(nProgInterrupts = n) }
    parser.opt[String]("reset-vec")
      .abbr("rstVec")
      .valueName("<addr-hex>")
      .foreach { str => brOptions = brOptions.copy(resetVec = BigInt(str, 16)) }
  }

  val optionsManager = new ExecutionOptionsManager("chisel3")
      with HasChiselExecutionOptions
      with HasFirrtlOptions
      with HasBROptions

  if (optionsManager.parse(args)) {
    Driver.execute(optionsManager, () => new BottleRocketPathChecker(optionsManager.brOptions)(config))
  }
}


class BottleRocketPathChecker(options: BROptions)(implicit p: config.Parameters) extends Module {
  val io = IO(new Bundle {
    val constclk = Input(Clock())
    val iBus = new GBX
    val dBus = new GBX
    val sBus = new GBX
    val localBus = new GBX
    val nmi = Input(Bool())
    val interruptLines = Input(UInt(width = options.nProgInterrupts.W))
    val dmi = Flipped(new DMIIO()(p))
    val wfisleep = Output(Bool())
    val traceInst = Output(UInt(width = xBitwidth))
    val traceRetire = Output(Bool())
    val traceInterrupt = Output(Bool())
    val traceEret = Output(Bool())
  })

  val core = Module(new BottleRocketCore(options))

  def dependsOn(bus: GBX): Bool = (bus.greqvalid ||
    bus.greqwrite ||
    bus.greqdvalid ||
    bus.greqlen =/= UInt(0) ||
    bus.greqaddr =/= UInt(0) ||
    bus.greqid =/= UInt(0) ||
    bus.greqsize =/= UInt(0) ||
    bus.greqdata =/= UInt(0) ||
    bus.grequser =/= UInt(0) ||
    bus.greqdlast)

  def connectOutputs(outerBus: GBX, innerBus: GBX): Unit = {
    outerBus.greqvalid := innerBus.greqvalid
    outerBus.greqwrite := innerBus.greqwrite
    outerBus.greqaddr := innerBus.greqaddr
    outerBus.greqlen := innerBus.greqlen
    outerBus.greqid := innerBus.greqid
    outerBus.greqdvalid := innerBus.greqdvalid
    outerBus.greqdata := innerBus.greqdata
    outerBus.greqsize := innerBus.greqsize
    outerBus.greqdlast := innerBus.greqdlast
    outerBus.grequser := innerBus.grequser
    outerBus.grspready := innerBus.grspready
  }

  def passthruInputs(outerBus: GBX, innerBus: GBX): Unit = {
    innerBus.grspvalid := outerBus.grspvalid
    innerBus.grspdata := outerBus.grspdata
    innerBus.grspwerr := outerBus.grspwerr
    innerBus.grsprerr := outerBus.grsprerr
    innerBus.grspid := outerBus.grspid
    innerBus.grsplast := outerBus.grsplast
    innerBus.grspuser := outerBus.grspuser
  }

  def alterInputs(bus: GBX): Unit = {
    bus.grspvalid := Bool(true)
    bus.grspdata := UInt(0)
    bus.grspwerr := Bool(true)
    bus.grsprerr := Bool(true)
    bus.grspid := UInt(0)
    bus.grsplast := Bool(true)
    bus.grspuser := UInt(0)
  }

  core.io.constclk := io.constclk
  core.io.nmi := io.nmi
  core.io.interruptLines := io.interruptLines
  core.io.dmi <> io.dmi
  io.wfisleep := core.io.wfisleep
  io.traceInst := core.io.traceInst
  io.traceRetire := core.io.traceRetire
  io.traceInterrupt := core.io.traceInterrupt
  io.traceEret := core.io.traceEret


  connectOutputs(io.iBus, core.io.iBus)
  connectOutputs(io.dBus, core.io.dBus)
  connectOutputs(io.sBus, core.io.sBus)
  connectOutputs(io.localBus, core.io.localBus)
  core.io.iBus.greqready := io.iBus.greqready
  core.io.dBus.greqready := io.dBus.greqready
  core.io.sBus.greqready := io.sBus.greqready
  core.io.localBus.greqready := io.localBus.greqready

  when (dependsOn(core.io.iBus) || dependsOn(core.io.dBus) || dependsOn(core.io.sBus) || dependsOn(core.io.localBus)) {
    passthruInputs(io.iBus, core.io.iBus)
    passthruInputs(io.dBus, core.io.dBus)
    passthruInputs(io.sBus, core.io.sBus)
    passthruInputs(io.localBus, core.io.localBus)
  } .otherwise {
    alterInputs(core.io.iBus)
    alterInputs(core.io.dBus)
    alterInputs(core.io.sBus)
    alterInputs(core.io.localBus)
  }

}
