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

// Chisel Bundle implementation of RISC-V Formal Interface (RVFI)

package bottlerocket

import chisel3._
import Params._

class RVFI extends Bundle {
  val valid = Output(Bool())
  val order = Output(UInt(64.W))
  val insn = Output(UInt(32.W))
  val trap = Output(Bool())
  val halt = Output(Bool())
  val intr = Output(Bool())
  val rs1_addr = Output(UInt(5.W))
  val rs1_rdata = Output(UInt(xBitwidth))
  val rs2_addr = Output(UInt(5.W))
  val rs2_rdata = Output(UInt(xBitwidth))
  val rd_addr = Output(UInt(5.W))
  val rd_wdata = Output(UInt(xBitwidth))
  val pc_rdata = Output(UInt(xBitwidth))
  val pc_wdata = Output(UInt(xBitwidth))
  val mem_addr = Output(UInt(xBitwidth))
  val mem_rmask = Output(UInt(4.W))
  val mem_wmask = Output(UInt(4.W))
  val mem_rdata = Output(UInt(xBitwidth))
  val mem_wdata = Output(UInt(xBitwidth))
}
