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

// This flop-based regfile has register number zero hardwired to zero

package bottlerocket

import chisel3._
import chisel3.util._
import Params._
import chisel3.internal.firrtl.Width

class RegfileWithZero(nRegs: Int, regWidth: Width, name: String = "regfileReverseIdx") {
  // nRegs must be positive power of two
  Predef.assert(nRegs > 0)
  Predef.assert((nRegs & (nRegs - 1)) == 0)
  private val idxMSB = log2Ceil(nRegs) - 1
  private val regs = Mem(nRegs-1, UInt(width = regWidth))
  regs.suggestName(name)
  private def toIndex(addr: UInt) = ~addr(idxMSB,0)
  def read(addr: UInt) = Mux(addr === 0.U, 0.U, regs.read(toIndex(addr)))
  def write(addr: UInt, data: UInt) = {
    when (addr =/= 0.U) {
      regs.write(toIndex(addr), data)
    }
  }
}
