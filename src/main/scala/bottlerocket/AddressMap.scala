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

// This address map multiplexes the multiple bus connections


package bottlerocket

import chisel3._
import Params._
import AddressMapConstants._
import InterruptConstants._

class AddressMap extends Module {
  val io = IO(new Bundle {
    val addr = Input(UInt(width = xBitwidth))
    val dBus = Output(Bool())
    val sBus = Output(Bool())
    val intCtrlBus = Output(Bool())
    val localBus = Output(Bool())
  })

  def below(max: UInt) = io.addr < max
  def startFrom(min: UInt) = io.addr >= min
  def range(min: UInt, max: UInt) = startFrom(min) && below(max)

  val sBase = UInt(0x2, width = xBitwidth) << UInt(28)
  val sGapBase = UInt(0xE, width = xBitwidth) << UInt(28)
  val sResumeBase = UInt(0xE01, width = xBitwidth) << UInt(20)

  // Memory
  io.dBus := below(DBUS_TOP)

  // Interrupt controller
  io.intCtrlBus := range(INT_BASE, INT_TOP)

  // Other local peripherals
  io.localBus := range(LOCAL_PERIPH_BASE, LOCAL_PERIPH_TOP) && !io.intCtrlBus

  // Other peripherals
  io.sBus := startFrom(DBUS_TOP) && !io.intCtrlBus && !io.localBus
}
