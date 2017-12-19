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

// Definition of the GBX bus interface

package bottlerocket

import chisel3._
import Params._

class GBX extends Bundle {
  val greqvalid = Output(Bool())
  val greqwrite = Output(Bool())
  val greqaddr = Output(UInt(busAddrBitwidth))
  val greqlen = Output(UInt(busLenBitwidth))
  val greqid = Output(UInt(busIDBitwidth))
  val greqdvalid = Output(Bool())
  val greqdata = Output(UInt(busDataBitwidth))
  val greqsize = Output(UInt(busSizeBitwidth))
  val greqdlast = Output(Bool())
  val grequser = Output(UInt(busUserBitwidth))
  val greqready = Input(Bool())
  val grspvalid = Input(Bool())
  val grspdata = Input(UInt(busDataBitwidth))
  val grspwerr = Input(Bool())
  val grsprerr = Input(Bool())
  val grspid = Input(UInt(busIDBitwidth))
  val grsplast = Input(Bool())
  val grspuser = Input(UInt(busUserBitwidth))
  val grspready = Output(Bool())
}
