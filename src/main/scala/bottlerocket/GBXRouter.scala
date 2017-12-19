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

// Router to allow the debug module and/or the core to master multiple buses

package bottlerocket

import chisel3._
import chisel3.util._
import Params._

class AddressDecode(nSlaves: Int) extends Bundle {
  val addr = Output(UInt(width = xBitwidth))
  val select = Input(UInt(width = log2Up(nSlaves)))
}

class GBXRouter(nMasters: Int, nSlaves: Int, nOutstanding: Int) extends Module {
  val io = IO(new Bundle {
    val masterSelect = Input(UInt(width = log2Ceil(nMasters)))
    val masters = Flipped(Vec(nMasters, new GBX))
    val slaves = Vec(nSlaves, new GBX)
    val decoder = new AddressDecode(nSlaves)
  })

  val mIdxWidth = log2Up(nMasters)
  val sIdxWidth = log2Up(nSlaves)

  val outstandingTracker = Module(new Queue(UInt(width = mIdxWidth + sIdxWidth), nOutstanding))

  // Decode address from selected master
  val activeReqMasterIdx = io.masterSelect
  val activeReqMaster = io.masters(activeReqMasterIdx)
  val activeReqSlaveIdx = io.decoder.select
  val activeReqSlave = io.slaves(activeReqSlaveIdx)
  val routerReady = outstandingTracker.io.enq.ready
  io.decoder.addr := activeReqMaster.greqaddr

  // Indicate ready to active request master
  for ((m, idx) <- io.masters.zipWithIndex) {
    val active = activeReqMasterIdx === UInt(idx)
    m.greqready := activeReqSlave.greqready && active && routerReady
  }

  // Fanout active request master signals to slaves
  for ((s, idx) <- io.slaves.zipWithIndex) {
    // Gating of control signals
    val active = activeReqSlaveIdx === UInt(idx)
    s.greqvalid := activeReqMaster.greqvalid && active && routerReady
    s.greqwrite := activeReqMaster.greqwrite && active && routerReady
    s.greqdvalid := activeReqMaster.greqdvalid && active && routerReady
    // Ordinary fanout of non-control signals
    s.greqaddr := activeReqMaster.greqaddr
    s.greqlen := activeReqMaster.greqlen
    s.greqid := activeReqMaster.greqid
    s.greqdata := activeReqMaster.greqdata
    s.greqsize := activeReqMaster.greqsize
    s.greqdlast := activeReqMaster.greqdlast
    s.grequser := activeReqMaster.grequser
    s.grspready := activeReqMaster.grspready
  }

  // Enqueue new request transaction (if any) to tracker
  outstandingTracker.io.enq.valid := activeReqMaster.greqvalid && !activeReqMaster.greqwrite && activeReqSlave.greqready
  outstandingTracker.io.enq.bits := chisel3.util.Cat(activeReqMasterIdx, activeReqSlaveIdx)

  // Select master, slave from head of tracker queue
  val activeRspMasterIdx = outstandingTracker.io.deq.bits(mIdxWidth + sIdxWidth - 1, sIdxWidth)
  val activeRspMaster = io.masters(activeRspMasterIdx)
  val activeRspSlaveIdx = outstandingTracker.io.deq.bits(sIdxWidth - 1, 0)
  val activeRspSlave = io.slaves(activeRspSlaveIdx)
  val routerHasOutstanding = outstandingTracker.io.deq.valid

  // Dequeue transaction if response arrives
  outstandingTracker.io.deq.ready := activeRspMaster.grspready && activeRspSlave.grspvalid && activeRspSlave.grsplast

  // Indicate ready to active response slave
  for ((s, idx) <- io.slaves.zipWithIndex) {
    val active = activeRspSlaveIdx === UInt(idx)
    s.grspready := activeRspMaster.grspready && active && routerHasOutstanding
  }

  val werr = Vec(io.slaves.map(s => s.grspvalid && s.grspwerr)).asUInt.orR

  // Fanout active response signals to masters
  for ((m, idx) <- io.masters.zipWithIndex) {
    // Special logic for error handling
    val active = activeRspMasterIdx === UInt(idx)
    m.grspvalid := active && ((activeRspSlave.grspvalid && routerHasOutstanding) || werr)
    m.grsprerr := activeRspSlave.grsprerr && active
    m.grspwerr := werr
    m.grsplast := activeRspSlave.grsplast && active && routerHasOutstanding
    // Ordinary fanout of non-control signals
    m.grspid := activeRspSlave.grspid
    m.grspuser := activeRspSlave.grspuser
    m.grspdata := activeRspSlave.grspdata
  }

}
