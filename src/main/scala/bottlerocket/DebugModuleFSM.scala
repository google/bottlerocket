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

// Debug Module that controls top-level behaviors of SiFive debug spec v0.13

package bottlerocket

import chisel3._
import chisel3.util.{Enum, Cat, Queue}
import freechips.rocketchip._
import devices.debug.{DMIReq, DMIResp, DMIIO, DMIConsts, DebugModuleParams}
import Params._

class DebugModuleFSM()(implicit p: config.Parameters) extends Module {
  val io = IO(new Bundle {
    val singleStepHalt = Input(Bool())
    val debugInt = Output(Bool())
    val debugRet = Output(Bool())
    val debugModeActive = Input(Bool())
    val gpraddr = Output(UInt(width = 5.W))
    val csraddr = Output(UInt(width = 12.W))
    val gprwrite = Output(Bool())
    val csrwrite = Output(Bool())
    val gprrdata = Input(UInt(width = xBitwidth))
    val csrrdata = Input(UInt(width = xBitwidth))
    val regwdata = Output(UInt(width = xBitwidth))
    val dmi = Flipped(new DMIIO()(p))
    val bus = new GBX
  })

  val reqQueue = Module(new Queue(new DMIReq(p(DebugModuleParams).nDMIAddrSize), 2))
  val respQueue = Module(new Queue(new DMIResp(), 2))

  reqQueue.io.enq <> io.dmi.req
  reqQueue.io.deq.ready := respQueue.io.enq.ready

  val dmiReqData = reqQueue.io.deq.bits.data
  val dmiReqAddr = reqQueue.io.deq.bits.addr
  val dmiReadActive = reqQueue.io.deq.fire && reqQueue.io.deq.bits.op === DMIConsts.dmi_OP_READ
  val dmiWriteActive = reqQueue.io.deq.fire && reqQueue.io.deq.bits.op === DMIConsts.dmi_OP_WRITE
  val dmiRespValid = Reg(init = Bool(false))
  val dmiRespType = Reg(init = DMIConsts.dmi_RESP_SUCCESS)
  val dmiRespData = Reg(UInt())

  dmiRespValid := reqQueue.io.deq.fire
  respQueue.io.enq.valid := dmiRespValid
  respQueue.io.enq.bits.resp := dmiRespType
  respQueue.io.enq.bits.data := dmiRespData
  io.dmi.resp <> respQueue.io.deq


  def zPad(w: Int) = UInt(0, width = w.W)

  val s_running :: s_halting :: s_triggering :: s_halted :: s_resuming :: Nil = chisel3.util.Enum(UInt(), 5)
  val haltState = Reg(init = s_running)

  val s_idle :: s_busy :: s_error :: Nil = chisel3.util.Enum(UInt(), 3)
  val cmdState = Reg(init = s_idle)

  // abstract data registers
  val data0 = Reg(init = zPad(32))

  // dmcontrol fields
  val haltReq = Reg(init = Bool(false))
  val resumeReq = Reg(init = Bool(false))
  val dmActive = Bool(true)

  // dmstatus fields
  val running = (haltState === s_running)
  val halted = (haltState === s_halted)
  val resumeAck = Reg(init = Bool(false))
  val version = UInt(2, width = 4.W)

  // abstractcs fields
  val progsize = zPad(5)
  val cmdBusy = (cmdState === s_busy)
  val cmdErr = Reg(init = zPad(3))
  val datacount = UInt(1, width = 5.W)

  // abstract cmd
  val abstractCmd = Reg(init = zPad(32))

  // submodule to manage system bus access
  val busFSM = Module(new DebugBusAccessFSM)
  busFSM.io.dmiReadActive := dmiReadActive
  busFSM.io.dmiWriteActive := dmiWriteActive
  busFSM.io.dmiReqAddr := dmiReqAddr
  busFSM.io.dmiReqData := dmiReqData
  io.bus <> busFSM.io.bus

  // read mapping
  dmiRespType := DMIConsts.dmi_RESP_SUCCESS
  when (busFSM.io.dmiRespValid) {
    dmiRespData := busFSM.io.dmiRespData
  } .elsewhen (dmiReqAddr === "h04".U) {
    dmiRespData := data0
  } .elsewhen (dmiReqAddr === "h10".U) {
    dmiRespData := Cat(haltReq, resumeReq, zPad(29), dmActive)
  } .elsewhen (dmiReqAddr === "h11".U) {
    dmiRespData := Cat(zPad(14), resumeAck, resumeAck, zPad(4), running, running, halted, halted, zPad(4), version)
  } .elsewhen (dmiReqAddr === "h16".U) {
    dmiRespData := Cat(zPad(3), progsize, zPad(11), cmdBusy, zPad(1), cmdErr, zPad(3), datacount)
  } .elsewhen (dmiReqAddr === "h17".U) {
    dmiRespData := abstractCmd
  } .otherwise {
    dmiRespType := DMIConsts.dmi_RESP_FAILURE
    dmiRespData := zPad(32)
  }

  val cmdType = abstractCmd(31,24)
  val newCmd = dmiWriteActive && (dmiReqAddr === "h17".U)
  val writeInCmd = (cmdState === s_busy) && dmiWriteActive
  val clearErr = dmiWriteActive && (dmiReqAddr === "h16".U) && dmiReqData(10,8).orR
  val invalidCmd = abstractCmd(31,24) =/= UInt(0)
  val isGPR = abstractCmd(15,5) === UInt(0x1000, width = 16)(15,5)
  val isCSR = abstractCmd(15,12) === UInt(0)
  val doRegAccess = (haltState === s_halted) && (cmdState === s_busy) && (cmdType === UInt(0)) && abstractCmd(17)
  val doRegWrite = doRegAccess && abstractCmd(16)
  val doRegRead = doRegAccess && !abstractCmd(16)

  // write mapping (except cmderr)
  when (dmiWriteActive) {
    when (dmiReqAddr === "h04".U) {
      data0 := dmiReqData
    } .elsewhen (dmiReqAddr === "h10".U) {
      haltReq := dmiReqData(31)
      resumeReq := dmiReqData(30)
      resumeAck := Bool(false)
    } .elsewhen (dmiReqAddr === "h17".U) {
      abstractCmd := dmiReqData
    }
  } .elsewhen (doRegRead) {
    data0 := Mux(isGPR, io.gprrdata, io.csrrdata)
  }

  val nextCmdState = Wire(UInt())
  nextCmdState := cmdState
  when (cmdState === s_idle) {
    when (newCmd) {
      nextCmdState := s_busy
    }
  } .elsewhen (cmdState === s_busy) {
    when (writeInCmd || invalidCmd || !halted) {
      nextCmdState := s_error
    } .otherwise {
      nextCmdState := s_idle
    }
  } .otherwise {
    nextCmdState := Mux(clearErr, s_idle, s_error)
  }
  cmdState := nextCmdState

  when (nextCmdState === s_error) {
    when (!writeInCmd) {
      cmdErr := UInt(1)
    } .elsewhen (!halted) {
      cmdErr := UInt(4)
    } .otherwise {
      cmdErr := UInt(2)
    }
  }

  // This signal ensures that each resume request causes only ONE resume
  val freshResumeReq = resumeReq && !resumeAck

  val nextHaltState = Wire(UInt())
  nextHaltState := haltState
  when (haltState === s_running) {
    when (io.singleStepHalt) {
      nextHaltState := s_halted
    } .elsewhen (haltReq) {
      nextHaltState := s_halting
    } .elsewhen (io.debugModeActive) {
      nextHaltState := s_halted // triggering
    }
  } .elsewhen (haltState === s_halting) {
    when (io.debugModeActive) {
      nextHaltState := s_halted
    }
  } .elsewhen (haltState === s_halted) {
    when (freshResumeReq) {
      nextHaltState := s_resuming
    }
  } .otherwise {
    when (!io.debugModeActive) {
      nextHaltState := s_running
      resumeAck := Bool(true)
    }
  }
  haltState := nextHaltState

  io.debugInt := haltState === s_halting
  io.debugRet := haltState === s_halted && nextHaltState === s_resuming
  io.gpraddr := abstractCmd(4,0)
  io.csraddr := abstractCmd(11,0)
  io.gprwrite := isGPR && doRegWrite
  io.csrwrite := isCSR && doRegWrite
  io.regwdata := data0
}
