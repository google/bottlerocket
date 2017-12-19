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

// Decoupled frontend implementation with efficient misaligned 32-bit fetch

package bottlerocket

import chisel3._
import chisel3.core.withClock
import chisel3.util.{Decoupled,Valid,Cat}
import freechips.rocketchip._
import rocket._
import Params._

class FrontendReq extends Bundle {
  val pc = Input(UInt(xBitwidth))
  val redirect = Input(Bool())
  val enter_U_mode = Input(Bool())
  val exit_U_mode = Input(Bool())
}

class FrontendResp extends Bundle {
  val pc = Output(UInt(xBitwidth))
  val inst = Output(UInt(instBitwidth))
  val error = Output(Bool())
}

class FrontendBuffer(options: BROptions) extends Module {
  val io = IO(new Bundle{
    val gclk = Input(Clock())
    val outstanding = Output(Bool())
    val sleeping = Input(Bool())
    val req = new FrontendReq
    val resp = Decoupled(new FrontendResp)
    val bus = new GBX
  })

  def wordAddress(x: UInt) = x & ~UInt(3, width = xBitwidth)
  def isRVC(i: UInt) = i(1,0) =/= UInt(3)
  def isWordAligned(a: UInt) = a(1,0) === UInt(0)
  def min(a: UInt, b: UInt) = Mux(a < b, a, b)

  // Hold GBX greqvalid low during sleep
  val greqvalid_ungated = Wire(Bool())
  io.bus.greqvalid := greqvalid_ungated && !io.sleeping

  // These two registers track issued GBX requests -> must be gated with gclk
  val n_pending = withClock(io.gclk) { Reg(UInt(3.W), init = UInt(0)) }
  val last_req_addr = withClock(io.gclk) { Reg(UInt(xBitwidth)) }
  io.outstanding := n_pending =/= UInt(0)

  val n_to_drop = Reg(UInt(3.W), init = UInt(0))
  val true_pending = n_pending - n_to_drop

  val buf_vec = Reg(Vec(4, UInt(16.W)))
  val err_vec = Reg(Vec(4, Bool()))
  val buf_base = Reg(UInt(xBitwidth), init = UInt(options.resetVec))
  val buf_size = Reg(UInt(4.W), init = UInt(0)) // SIZE IS IN BYTES
  val buf_head = Reg(UInt(2.W), init = UInt(0)) // PTRS ARE HALFWORD INDICES
  val buf_tail = Reg(UInt(2.W), init = UInt(0)) // PTRS ARE HALFWORD INDICES
  val buffer_full = (buf_size + (true_pending << 2)) > UInt(4) || n_pending === UInt(7)

  val clear_buffer = Wire(Bool())
  val drop_outstanding = Wire(Bool())
  val expected_bus_fetch_valid = io.bus.grspvalid && n_to_drop === UInt(0)

  val head_halfword = buf_vec(buf_head)
  val next_halfword = buf_vec(buf_head + UInt(1))
  val jumped_to_halfword_aligned = Reg(init = Bool(false))
  val n_useful_gbx_bytes = Mux(jumped_to_halfword_aligned, UInt(2), UInt(4))
  val bus_first_halfword = Mux(jumped_to_halfword_aligned, io.bus.grspdata(31,16), io.bus.grspdata(15,0))
  val bus_second_halfword = Mux(jumped_to_halfword_aligned, io.bus.grspdata(15,0), io.bus.grspdata(31,16))

  val req_prv = Wire(UInt())
  val prev_req_prv = Reg(init = UInt(PRV.M))

  val hold_reset = Reg(init = Bool(true))
  hold_reset := Bool(false)

  // unused GBX fields
  io.bus.grspready := Bool(true)
  io.bus.greqwrite := Bool(false)
  io.bus.greqlen := UInt(0)
  io.bus.greqdvalid := Bool(false)
  io.bus.greqdata := UInt(0)
  io.bus.greqsize := UInt(2)
  io.bus.greqdlast := Bool(false)
  io.bus.grequser := UInt(0)

  // Sometimes redirects go to halfword-aligned addresses
  when (io.req.redirect) {
    jumped_to_halfword_aligned := !isWordAligned(io.req.pc)
  } .elsewhen (expected_bus_fetch_valid) {
    jumped_to_halfword_aligned := Bool(false)
  }

  // Record last requested address
  when (io.bus.greqready && io.bus.greqvalid) {
    last_req_addr := io.bus.greqaddr
  }

  // Privilege modes
  req_prv := prev_req_prv
  when (io.req.enter_U_mode) {
    req_prv := UInt(PRV.U)
  } .elsewhen (io.req.exit_U_mode) {
    req_prv := UInt(PRV.M)
  }
  prev_req_prv := req_prv
  io.bus.greqid := Mux(req_prv === UInt(PRV.M), UInt(2), UInt(0))

  // two main behaviors: branch/jump/exception/etc or sequential code
  // ALL privilege level changes are also redirects, so this handles flushing
  when (hold_reset) {
    greqvalid_ungated := Bool(false)
    io.bus.greqaddr := wordAddress(io.req.pc)
    clear_buffer := Bool(false)
    drop_outstanding := Bool(false)
  } .elsewhen (io.req.redirect) {
    greqvalid_ungated := Bool(true)
    io.bus.greqaddr := wordAddress(io.req.pc)
    clear_buffer := Bool(true)
    drop_outstanding := Bool(true)
  } .otherwise {
    greqvalid_ungated := !buffer_full
    io.bus.greqaddr := Mux(true_pending > UInt(0), last_req_addr + UInt(4), wordAddress(buf_base + buf_size))
    clear_buffer := Bool(false)
    drop_outstanding := Bool(false)
  }

  // outstanding / to-drop transaction counters
  // Never more than one USEFUL outstanding transaction!
  val n_pending_next = n_pending +
    Mux(io.bus.greqready && io.bus.greqvalid, UInt(1), UInt(0)) -
    Mux(io.bus.grspvalid, UInt(1), UInt(0))
  n_pending := n_pending_next
  when (drop_outstanding) {
    n_to_drop := n_pending - Mux(io.bus.grspvalid, UInt(1), UInt(0))
  } .elsewhen (n_to_drop =/= UInt(0) && io.bus.grspvalid) {
    n_to_drop := n_to_drop - UInt(1)
  }

  // buffer control path
  when (clear_buffer) {
    buf_size := UInt(0)
    buf_base := io.req.pc
    buf_tail := UInt(0)
    buf_head := UInt(0)
  } .otherwise {
    val resp_inst_size = Mux(isRVC(io.resp.bits.inst), UInt(2), UInt(4))
    val base_diff_bytes = Mux(io.resp.fire, resp_inst_size, UInt(0))
    val end_diff_bytes = Mux(expected_bus_fetch_valid, n_useful_gbx_bytes, UInt(0))
    val head_diff = base_diff_bytes >> 1
    val tail_diff = end_diff_bytes >> 1
    buf_head := buf_head + head_diff
    buf_base := buf_base + base_diff_bytes
    buf_tail := buf_tail + tail_diff
    buf_size := min(buf_size + end_diff_bytes - base_diff_bytes, UInt(8))
  }

  val reply_error = expected_bus_fetch_valid && io.bus.grsprerr

  // buffer refill writes:
  // All replies are already filtered with 'expected_bus_fetch_valid'
  // Therefore, when bus reply appears, take it!
  when (expected_bus_fetch_valid) {
    buf_vec(buf_tail) := bus_first_halfword
    // Second half is only undesired after a branch
    // In this case, all buffer contents are garbage, so writing wastefully is fine
    buf_vec(buf_tail + UInt(1)) := bus_second_halfword
    err_vec(buf_tail) := reply_error
    err_vec(buf_tail + UInt(1)) := reply_error
  }

  // reply management
  io.resp.bits.pc := buf_base
  when (buf_size === UInt(0)) {
    io.resp.valid := expected_bus_fetch_valid && (!jumped_to_halfword_aligned || isRVC(bus_first_halfword))
    io.resp.bits.inst := Cat(bus_second_halfword, bus_first_halfword)
    io.resp.bits.error := reply_error
  } .elsewhen (buf_size === UInt(2) && !isRVC(buf_vec(buf_head))) {
    io.resp.valid := expected_bus_fetch_valid
    io.resp.bits.inst := Cat(bus_first_halfword, head_halfword)
    io.resp.bits.error := reply_error || err_vec(buf_head)
  } .otherwise {
    io.resp.valid := Bool(true)
    io.resp.bits.inst := Cat(next_halfword, head_halfword)
    io.resp.bits.error := err_vec(buf_head) || err_vec(buf_head + UInt(1))
  }

}
