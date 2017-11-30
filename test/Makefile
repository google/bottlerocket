# Copyright 2017 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

RISCV_TEST_BASE = ../third_party/rocket-chip/riscv-tools/riscv-tests
GEN_SRC_DIR = ../generated-src

RISCV_GCC = riscv32-unknown-elf-gcc
RISCV_GCC_OPTS = -static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles
RISCV_OBJDUMP = riscv32-unknown-elf-objdump

NCVERILOG_OPTS = +incdir+$(GEN_SRC_DIR) +sv +nctimescale+1ns/1ns +access+r +nclicq

SIM ?= ncverilog
SIM_OPTS ?= $(NCVERILOG_OPTS)

# fence_i not supported for Harvard architecture
RV32UI_TESTS = addi \
               add \
               andi \
               and \
               auipc \
               beq \
               bge \
               bgeu \
               blt \
               bltu \
               bne \
               jalr \
               jal \
               lb \
               lbu \
               lh \
               lhu \
               lui \
               lw \
               ori \
               or \
               sb \
               sh \
               simple \
               slli \
               sll \
               slti \
               sltiu \
               slt \
               sltu \
               srai \
               sra \
               srli \
               srl \
               sub \
               sw \
               xori \
               xor

RV32MI_TESTS = breakpoint \
               csr \
               illegal \
               ma_addr \
               ma_fetch \
               mcsr \
               sbreak \
               scall \
               shamt

TESTS = $(RV32UI_TESTS) # $(RV32MI_TESTS)

default: $(addsuffix .log, $(TESTS))

%.log: %.hex %.elf $(GEN_SRC_DIR)/*.v *.v
	$(RISCV_OBJDUMP) -D $*.elf > $*.objdump
	$(SIM) $(SIM_OPTS) +image=$* BottleRocketCoreTB.v &> $*.log

%.hex: %.elf
	elf2hex 4 32768 $< > $@

%.elf: $(RISCV_TEST_BASE)/isa/rv32ui/%.S
	$(RISCV_GCC) $(RISCV_GCC_OPTS) -I$(RISCV_TEST_BASE)/env/p -I$(RISCV_TEST_BASE)/isa/macros/scalar -Ttest.ld -o $@ $<

%.elf: $(RISCV_TEST_BASE)/isa/rv32mi/%.S
	$(RISCV_GCC) $(RISCV_GCC_OPTS) -I$(RISCV_TEST_BASE)/env/p -I$(RISCV_TEST_BASE)/isa/macros/scalar -Ttest.ld -o $@ $<

.SECONDARY: $(addsuffix .elf, $(TESTS)) $(addsuffix .hex, $(TESTS))

.PHONY: clean

clean:
	rm -rf *.hex *.elf *.log ncverilog* INCA_libs data *.log *.dsn *.trn *.dump.d
