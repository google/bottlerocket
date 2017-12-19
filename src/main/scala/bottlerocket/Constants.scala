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

// BottleRocket-specific constant values related to the address space

package bottlerocket

import chisel3._
import chisel3.util.BitPat
import chisel3.internal.firrtl.Width

package object AddressMapConstants {
  val DBUS_TOP = UInt("h10000000")
  val LOCAL_PERIPH_BASE = UInt("hA0000000")
  val LOCAL_PERIPH_TOP = UInt("hA000FFFF")
}

package object InterruptConstants {
  val INT_BASE             = UInt("hA0000000")
  val INT_TOP              = UInt("hA0000FFF")
  val INT_ACTIVE_IDX_ADDR  = UInt("hA0000000")
  val INT_TABLE_LOC_ADDR   = UInt("hA0000004")
  val INT_ENABLE_BASE      = UInt("hA0000100")
  val INT_DISABLE_BASE     = UInt("hA0000200")
  val INT_SET_BASE         = UInt("hA0000300")
  val INT_CLEAR_BASE       = UInt("hA0000400")
  val INT_ENABLE_CTRL_PAT  = BitPat("b01?00???00")
  val INT_PENDING_CTRL_PAT = BitPat("b10?00???00")
}
