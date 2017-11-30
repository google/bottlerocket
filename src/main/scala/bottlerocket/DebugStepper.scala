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

// Debug stimulus generator that single steps the core continuously

package bottlerocket

import chisel3._
import chisel3.util.{Enum,Cat}
import freechips.rocketchip._
import devices.debug.{DMIReq, DMIResp, DMIIO, DMIConsts}
import Params._

class DebugStepper()(implicit p: config.Parameters) extends Module {
  val io = IO(new Bundle {
    val dmi = new DMIIO()(p)
  })

  // running: wait for halted to be set
  // halted: set resumereq
  // resuming: wait for resumeack

  val s_waitForHalt :: s_restart :: s_waitForResume :: Nil = chisel3.util.Enum(UInt(), 3)
  val haltState = Reg(init = s_waitForHalt)

  val nextHaltState = Wire(UInt())
  nextHaltState := haltState

  io.dmi.req.valid := Bool(true)
  io.dmi.resp.ready := Bool(true)
  io.dmi.req.bits.addr := Mux(haltState === s_restart, "h10".U, "h11".U)
  io.dmi.req.bits.data := "h40000001".U
  io.dmi.req.bits.op := Mux(haltState === s_restart, DMIConsts.dmi_OP_WRITE, DMIConsts.dmi_OP_READ)

  when (haltState === s_waitForHalt) {
    when (io.dmi.resp.fire && io.dmi.resp.bits.data(8)) {
      haltState := s_restart
    }
  } .elsewhen (haltState === s_restart) {
    when (io.dmi.req.fire) {
      haltState := s_waitForResume
    }
  } .otherwise {
    when (io.dmi.resp.fire && io.dmi.resp.bits.data(16)) {
      haltState := s_waitForHalt
    }
  }
}
