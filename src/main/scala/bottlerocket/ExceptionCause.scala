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

// Exception / interrupt cause encodings

package bottlerocket

import chisel3._
import chisel3.util.MuxCase
import freechips.rocketchip.rocket.{CSR, Causes}

class ExceptionCause extends Bundle {
  val misalignedFetch = Bool()
  val illegalFetch = Bool()
  val illegalInstruction = Bool()
  val breakpoint = Bool()
  val debugBreakpoint = Bool()
  val loadMisaligned = Bool()
  val loadFault = Bool()
  val storeMisaligned = Bool()
  val storeFault = Bool()
  val interrupt = Bool()
}

object ExceptionCause {
  def clear: ExceptionCause = 0.U.asTypeOf(new ExceptionCause)

  def toBool(e: ExceptionCause): Bool = e.asUInt.orR

  def toCause(eCause: ExceptionCause, iCause: UInt): UInt = {
    MuxCase(iCause, Seq(
      (eCause.misalignedFetch, UInt(Causes.misaligned_fetch)),
      (eCause.illegalFetch, UInt(Causes.fetch_access)),
      (eCause.illegalInstruction, UInt(Causes.illegal_instruction)),
      (eCause.breakpoint, UInt(Causes.breakpoint)),
      (eCause.debugBreakpoint, UInt(CSR.debugTriggerCause)),
      (eCause.loadMisaligned, UInt(Causes.misaligned_load)),
      (eCause.loadFault, UInt(Causes.load_access)),
      (eCause.storeMisaligned, UInt(Causes.misaligned_store)),
      (eCause.storeFault, UInt(Causes.store_access))))
  }
}
