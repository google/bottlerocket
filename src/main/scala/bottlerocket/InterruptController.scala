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

// Programmable interrupt controller

package bottlerocket

import chisel3._
import chisel3.util._
import chisel3.core.withClock
import Params._
import InterruptConstants._

class GBXInterruptController(nProgInterrupts: Int) extends Module {
  val io = IO(new Bundle {
    val constclk = Input(Clock())
    val bus = Flipped(new GBX)
    val lines = Input(UInt(width = nProgInterrupts.W))
    val eip = Output(Bool())
  })

  val req_active = Reg(init = Bool(false))
  val req_addr = Reg(init = UInt(0))
  val req_write = Reg(init = Bool(false))
  val req_wdata = Reg(init = UInt(0))
  val req_id = Reg(init = UInt(0))
  val req_user = Reg(init = UInt(0))

  val validfield = req_addr(9,0) === INT_ENABLE_CTRL_PAT || req_addr(9,0) === INT_PENDING_CTRL_PAT ||
    req_addr(9,0) === INT_ACTIVE_IDX_ADDR(9,0) || req_addr(9,0) === INT_TABLE_LOC_ADDR(9,0)

  val badaddr = req_addr(31,10) =/= INT_BASE(31,10) || !validfield

  io.bus.greqready := !req_active
  when (!req_active) {
    when (io.bus.greqvalid) {
      req_active := Bool(true)
      req_addr := io.bus.greqaddr
      req_write := io.bus.greqwrite && io.bus.greqdvalid
      req_wdata := io.bus.greqdata
      req_id := io.bus.greqid
      req_user := io.bus.grequser
    }
  } .elsewhen(req_write || io.bus.grspready) {
    req_active := Bool(false)
  }
  io.bus.grspvalid := req_active && !req_write
  io.bus.grspwerr := req_active && req_write && badaddr
  io.bus.grsprerr := req_active && !req_write && badaddr
  io.bus.grspid := req_id
  io.bus.grsplast := Bool(true)
  io.bus.grspuser := req_user

  def boolToMask(flag: Bool) = Cat(Seq.fill(nProgInterrupts)(flag))
  def wordVector(bitVector: UInt) = {
    val bitVectorNumWords = (bitVector.getWidth + xBitwidth.get - 1) / xBitwidth.get
    val bitVectorExpandedWidth = (bitVectorNumWords * xBitwidth.get).W
    val padded = Wire(UInt(width = bitVectorExpandedWidth))
    padded := bitVector
    padded.asTypeOf(Vec(bitVectorNumWords, UInt(width = xBitwidth)))
  }

  val wdata_expanded = Wire(UInt(width = nProgInterrupts.W))
  wdata_expanded := (req_wdata << (req_addr(4,2) * UInt(32))) & boolToMask(req_active && req_write)

  val enable = wdata_expanded & boolToMask(req_addr(31,5) === INT_ENABLE_BASE(31,5))
  val disable = wdata_expanded & boolToMask(req_addr(31,5) === INT_DISABLE_BASE(31,5))
  val set = wdata_expanded & boolToMask(req_addr(31,5) === INT_SET_BASE(31,5))
  val clear = wdata_expanded & boolToMask(req_addr(31,5) === INT_CLEAR_BASE(31,5))

  val intActive = withClock(io.constclk) { Reg(init = Bool(false)) }
  val activeIntIdx = withClock(io.constclk) { Reg(UInt(width = log2Ceil(nProgInterrupts).W), init = UInt(0)) }
  val tableBase = Reg(init = UInt(0, width = xBitwidth))
  val enabled = Reg(UInt(width = nProgInterrupts.W), init = boolToMask(Bool(false))) // TODO (amagyar): all enabled at reset?
  val pending = withClock(io.constclk) { Reg(init = UInt(0, width = nProgInterrupts.W)) }
  val canTake = enabled & pending & ~boolToMask(intActive)
  val taken = PriorityEncoderOH(canTake)

  val complete = req_active && req_write && req_addr === INT_ACTIVE_IDX_ADDR && !req_wdata(0)
  val disablingActive = req_active && req_write && req_addr(31,5) === INT_DISABLE_BASE(31,5) && req_addr(4,2) === activeIntIdx(7,5) && req_wdata(activeIntIdx(4,0))

  enabled := (enabled | enable) & ~(disable)
  pending := (pending | set | io.lines) & ~(clear | taken)

  when (canTake.orR) {
    intActive := Bool(true)
    activeIntIdx := PriorityEncoder(canTake)
  } .elsewhen (complete || disablingActive) {
    intActive := Bool(false)
  }

  when (req_addr(9,0) === INT_TABLE_LOC_ADDR(9,0) && req_active && req_write) {
    tableBase := req_wdata
  }

  io.eip := intActive

  val enabledVector = wordVector(enabled)
  val pendingVector = wordVector(pending)
  when (req_addr(31,5) === INT_ENABLE_BASE(31,5) || req_addr(31,5) === INT_DISABLE_BASE(31,5)) {
    io.bus.grspdata := enabledVector(req_addr(4,2))
  } .elsewhen (req_addr(31,5) === INT_SET_BASE(31,5) || req_addr(31,5) === INT_CLEAR_BASE(31,5)) {
    io.bus.grspdata := pendingVector(req_addr(4,2))
  } .elsewhen (req_addr(9,0) === INT_TABLE_LOC_ADDR(9,0)) {
    io.bus.grspdata := tableBase
  } .otherwise {
    io.bus.grspdata := Cat(activeIntIdx,intActive)
  }

}
