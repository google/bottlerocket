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

// Structural definition of AXI4Lite channel bundles

package bottlerocket

import chisel3._
import chisel3.util.Irrevocable
import freechips.rocketchip
import rocketchip.util.GenericParameterizedBundle
import rocketchip.amba.axi4.AXI4BundleParameters

abstract class AXI4LiteBundleBase(params: AXI4BundleParameters) extends GenericParameterizedBundle(params)

abstract class AXI4LiteBundleA(params: AXI4BundleParameters) extends AXI4LiteBundleBase(params)
{
  val addr   = UInt(width = params.addrBits)
  val cache  = UInt(width = params.cacheBits)
  val prot   = UInt(width = params.protBits)
}

class AXI4LiteBundleAW(params: AXI4BundleParameters) extends AXI4LiteBundleA(params)
class AXI4LiteBundleAR(params: AXI4BundleParameters) extends AXI4LiteBundleA(params)

class AXI4LiteBundleW(params: AXI4BundleParameters) extends AXI4LiteBundleBase(params)
{
  val data = UInt(width = params.dataBits)
  val strb = UInt(width = params.dataBits/8)
}

class AXI4LiteBundleR(params: AXI4BundleParameters) extends AXI4LiteBundleBase(params)
{
  val data = UInt(width = params.dataBits)
  val resp = UInt(width = params.respBits)
}

class AXI4LiteBundleB(params: AXI4BundleParameters) extends AXI4LiteBundleBase(params)
{
  val resp = UInt(width = params.respBits)
}

class AXI4LiteBundle(params: AXI4BundleParameters) extends AXI4LiteBundleBase(params)
{
  val aw = Irrevocable(new AXI4LiteBundleAW(params))
  val w  = Irrevocable(new AXI4LiteBundleW (params))
  val b  = Flipped(Irrevocable(new AXI4LiteBundleB (params)))
  val ar = Irrevocable(new AXI4LiteBundleAR(params))
  val r  = Flipped(Irrevocable(new AXI4LiteBundleR (params)))
}

object AXI4LiteBundle
{
  def apply(params: AXI4BundleParameters) = new AXI4LiteBundle(params)
}
