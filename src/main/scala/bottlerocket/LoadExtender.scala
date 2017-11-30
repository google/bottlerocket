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

// Load data extender

package bottlerocket

import chisel3._
import chisel3.util.Cat
import freechips.rocketchip._
import rocket._
import Params._

class LoadExtender extends Module {
  val io = IO(new Bundle {
    val offset = Input(UInt(width = xByteOffsetBitwidth))
    val in = Input(UInt(width = xBitwidth))
    val memType = Input(UInt(width = MT_SZ))
    val out = Output(UInt(width = xBitwidth))
  })

  val outSigned = Wire(SInt(width = xBitwidth))

  val inByteSwizzled = Mux(io.offset(0),
                           Cat(io.in(23,16), io.in(31,24), io.in(7,0), io.in(15,8)),
                           io.in)

  val inSwizzled = Mux(io.offset(1),
                       Cat(inByteSwizzled(15,0), inByteSwizzled(31,16)),
                       inByteSwizzled)

  outSigned := inSwizzled.asSInt
  io.out := outSigned.asUInt

  when (io.memType === MT_B) {
    outSigned := inSwizzled(7,0).asSInt
  } .elsewhen(io.memType === MT_H) {
    outSigned := inSwizzled(15,0).asSInt
  } .elsewhen(io.memType === MT_BU) {
    io.out := inSwizzled(7,0)
  } .elsewhen(io.memType === MT_HU) {
    io.out := inSwizzled(15,0)
  }

}
