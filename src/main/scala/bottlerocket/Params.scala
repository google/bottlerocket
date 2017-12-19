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

// BottleRocket parameters

package bottlerocket

import chisel3._
import chisel3.internal.firrtl.Width
import freechips.rocketchip._
import config.{Config, Field}
import system.TinyConfig
import rocket.{RocketCoreParams, MulDivParams, DCacheParams, ICacheParams}
import tile._
import coreplex._
import devices.debug.{DebugModuleParams, DefaultDebugModuleParams}

package object Params {
  val busAddrBitwidth = 32.W
  val busSizeBitwidth = 2.W
  val busLenBitwidth = 4.W
  val busDataBitwidth = 32.W
  val busUserBitwidth = 16.W
  val busIDBitwidth = 16.W

  val xBitwidth = 32.W
  val halfXBitwidth = 16.W
  val xByteOffsetBitwidth = 2.W
  val instBitwidth = 32.W
  val nRegisters = 32
}

case object NMI_VEC extends Field[BigInt]

class TileConfig extends Config((site, here, up) => {
  case TileKey => RocketTileParams(
    core = RocketCoreParams(
      useUser = true,
      useVM = false,
      useAtomics = false,
      nBreakpoints = 4,
      nPMPs = 0,
      fpu = None,
      mulDiv = Some(MulDivParams(mulUnroll = 8))),
    btb = None,
    dcache = Some(DCacheParams()),
    icache = Some(ICacheParams()))
  case BuildRoCC => false
  case DebugModuleParams => DefaultDebugModuleParams(site(XLen))
  case NMI_VEC => BigInt("100", 16)
})

class DefaultBottleRocketConfig extends Config(new TileConfig ++ new TinyConfig)
