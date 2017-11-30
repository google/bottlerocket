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

// Generator for emitting Verilog

package bottlerocket

import chisel3._
import firrtl.{ExecutionOptionsManager, HasFirrtlOptions, CommonOptions, FirrtlExecutionOptions, ComposableOptions}

case class BROptions(nProgInterrupts: Int = 240, resetVec: BigInt = BigInt("100", 16)) extends ComposableOptions

object BottleRocketGenerator extends App {
  val config = new DefaultBottleRocketConfig

  trait HasBROptions {
    self: ExecutionOptionsManager =>
    var brOptions = BROptions()
    parser.note("BottleRocket options")
    parser.opt[Int]("nProgInterrupts")
      .abbr("nInts")
      .valueName("<nInts>")
      .foreach { n => brOptions = brOptions.copy(nProgInterrupts = n) }
    parser.opt[String]("reset-vec")
      .abbr("rstVec")
      .valueName("<addr-hex>")
      .foreach { str => brOptions = brOptions.copy(resetVec = BigInt(str, 16)) }
  }

  val optionsManager = new ExecutionOptionsManager("chisel3")
      with HasChiselExecutionOptions
      with HasFirrtlOptions
      with HasBROptions { }

  if (optionsManager.parse(args)) {
    Driver.execute(optionsManager, () => new BottleRocketCore(optionsManager.brOptions)(config))
  }
}
