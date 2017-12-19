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

// Module to allow debug system bus access

package bottlerocket

import chisel3._
import chisel3.util.{Enum, Cat, Queue, MuxCase, RegEnable}
import freechips.rocketchip._
import devices.debug.{DMIReq, DMIResp, DMIIO, DMIConsts, DebugModuleParams}
import Params._

class DebugBusAccessFSM()(implicit p: config.Parameters) extends Module {
  val io = IO(new Bundle {
    val dmiReadActive = Input(Bool())
    val dmiWriteActive = Input(Bool())
    val dmiReqAddr = Input(UInt(width = p(DebugModuleParams).nDMIAddrSize.W))
    val dmiReqData = Input(UInt(width = xBitwidth))
    val dmiRespValid = Output(Bool())
    val dmiRespData = Output(UInt(width = xBitwidth))
    val bus = new GBX
  })

  val s_idle :: s_reqWait :: s_readWait :: Nil = chisel3.util.Enum(UInt(), 3)
  val state = Reg(init = s_idle)
  val nextState = Wire(UInt())
  val errorState = Reg(init = UInt(0, width = 3.W))

  val stale = Reg(init = Bool(true))
  val d0_reg = Reg(UInt(width = xBitwidth))
  val a0_reg = Reg(UInt(width = xBitwidth))

  val isCSAddr = io.dmiReqAddr === "h38".U
  val isA0Addr = io.dmiReqAddr === "h39".U
  val isD0Addr = io.dmiReqAddr === "h3c".U

  // Control/Status register management
  val accessSize = Reg(init = UInt(2, width = 3.W))
  val addrIncr = UInt(1) << accessSize
  val autoIncrement = Reg(init = Bool(false))
  val autoRead = Reg(init = Bool(false))
  val busSize = UInt(32, width = 7.W)
  val sizeMask = UInt(7, width = 5.W)
  val csReadVal = Cat(
    UInt(0, width = 12.W),
    accessSize,
    autoIncrement,
    autoRead,
    errorState,
    busSize,
    sizeMask)

  // Update control and status fields
  when (io.dmiWriteActive && isCSAddr) {
    accessSize := io.dmiReqData(19,17)
    autoIncrement := io.dmiReqData(16)
    autoRead := io.dmiReqData(15)
  }

  // Determine if the host is trying to start a new request
  val triggerSingleRead = io.dmiWriteActive && isCSAddr && io.dmiReqData(20)
  val triggerAutoRead = autoRead && io.dmiReadActive && isD0Addr
  val triggerSingleWrite = io.dmiWriteActive && isD0Addr
  val trigger = triggerSingleRead || triggerAutoRead || triggerSingleWrite

  // Record GBX fields upon entering transaction
  val latchRequest = state === s_idle && nextState === s_reqWait
  val reqWrite = RegEnable(next = triggerSingleWrite, enable = latchRequest, init = Bool(false))
  val reqSize = RegEnable(next = accessSize, enable = latchRequest)

  // Error tracking
  val misaligned = (a0_reg & (addrIncr - UInt(1))) =/= UInt(0)
  when (state =/= s_idle && (io.dmiWriteActive || io.dmiReadActive) && (isA0Addr || isD0Addr)) {
    errorState := UInt(4)
  } .elsewhen (state === s_idle && trigger && misaligned) {
    errorState := UInt(3)
  } .elsewhen (io.bus.grspvalid && io.bus.grspwerr) {
    errorState := UInt(3)
  } .elsewhen (io.bus.grspvalid && io.bus.grsprerr) {
    errorState := UInt(2)
  } .elsewhen (io.dmiWriteActive && isCSAddr) {
    errorState := errorState & (~io.dmiReqData(14,12))
  }

  // State management
  nextState := state
  when (state === s_idle) {
    when (trigger && !misaligned && errorState === UInt(0)) {
      nextState := s_reqWait
    }
  } .elsewhen (state === s_reqWait) {
    when (io.bus.greqready) {
      nextState := Mux(reqWrite, s_idle, s_readWait)
    }
  } .otherwise {
    nextState := Mux(io.bus.grspvalid && !io.bus.grspwerr, s_idle, s_readWait)
  }
  state := nextState

  val byteOffsetInBits = a0_reg(1,0) << UInt(3)

  // Data register management
  val ctrlRegWriteable = state === s_idle && errorState === UInt(0)
  when (io.bus.grspvalid) {
    stale := Bool(false)
    d0_reg := io.bus.grspdata >> byteOffsetInBits
  } .elsewhen ((io.dmiReadActive || io.dmiWriteActive) && isD0Addr) {
    stale := Bool(true)
    when (io.dmiWriteActive && ctrlRegWriteable) {
      d0_reg := io.dmiReqData
    }
  }

  // Address register management
  val triggerAutoIncrement = autoIncrement && io.bus.grspvalid
  when (io.dmiWriteActive && isA0Addr && ctrlRegWriteable) {
    a0_reg := io.dmiReqData
  } .elsewhen (triggerAutoIncrement) {
    a0_reg := a0_reg + addrIncr
  }

  io.bus.greqvalid := state === s_reqWait
  io.bus.greqwrite := state === s_reqWait && reqWrite
  io.bus.greqaddr := a0_reg
  io.bus.greqlen := UInt(0)
  io.bus.greqid := UInt(2) // privileged access
  io.bus.greqdvalid := state === s_reqWait && reqWrite
  io.bus.greqdata := d0_reg << byteOffsetInBits
  io.bus.greqsize := reqSize
  io.bus.greqdlast := Bool(true)
  io.bus.grequser := UInt(0)

  io.bus.grspready := Bool(true)

  // Read mapping
  io.dmiRespValid := (io.dmiReadActive || io.dmiWriteActive) && (isCSAddr || isA0Addr || isD0Addr)
  when (isCSAddr) {
    io.dmiRespData := csReadVal
  } .elsewhen (isA0Addr) {
    io.dmiRespData := a0_reg
  } .otherwise {
    io.dmiRespData := d0_reg
  }

}
