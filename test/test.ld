/* Copyright 2017 Google LLC
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
*/

OUTPUT_ARCH( "riscv" )
ENTRY(_start)

SECTIONS
{
  . = 0x0004100;
  .text.init : AT(ADDR(.text.init)) { *(.text.init) }
  .tohost ALIGN(0x2000) : AT(ADDR(.tohost)) { *(.tohost) }
  .text ALIGN(0x1000) : AT(ADDR(.text)) { *(.text) }
  .data ALIGN(0x1000) : AT(ADDR(.data)) { *(.data) }
  .bss : AT(ADDR(.bss)) { *(.bss) }
  _end = .;
}

