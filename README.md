# BottleRocket RV32IMC Core

This is not an officially supported Google product.

## Overview

BottleRocket is a 32-bit, RISC-V microcontroller-class processor core that is
built as a customized microarchitecture from components of the Free Chips
Project Rocket core. It is implemented in the Chisel HDL, and it consists of a
basic, 3-stage pipeline with separate instruction and data ARM AMBA AXI4Lite
buses. It has an assortment of features that are designed to support typical use
as a control processor for memory-mapped devices.

## Features

* RV32IMC ISA, Privileged Architecture v1.10
   * 32-bit RISC-V base instruction set (‘RV32I’)
   * Hardware integer multiplier/divider (‘M’ standard extension)
   * 16-bit compressed instruction support (‘C’ standard extension)
* Machine (‘M’) and user (‘U’) privilege modes

## Design Rationale

The BottleRocket core is designed to be as simple as possible to allow for easy,
application-specific changes. It uses several key components from Rocket Chip,
an open-source RISC-V chip generator framework, including the instruction
decoder and control & status register (CSR) finite state machine. These two
components are responsible for implementing the majority of the nuanced features
of the user ISA and the privileged architecture, respectively. This approach has
several key advantages.

* Rocket Chip is the reference implementation of the RISC-V ISA. It is largely
  produced by the primary authors of the ISA specifications, and it is by far
  the most spec-compliant hardware implementation.

* However, Rocket Chip is quite complex. It has many performance-oriented
  microarchitectural features (integrated non-blocking data cache, branch
  prediction)

* Rocket Chip is a very heavily metaprogrammed framework for generating
  symmetric multiprocessor (SMP) systems with interconnect supporting multiple
  coherent caches. In order to use the core in a simpler context, creating a
  simpler top-level module would be desirable for readability purposes.

* The “Rocket” core that is used in Rocket Chip is largely composed of
  well-factored, reasonably large, and highly-reusable sub-blocks. These blocks
  have been used in multiple projects to create different core microarchitectures
  or pipelines with relatively low effort (BOOM, ZScale)

* The instruction decoder is implemented as a reusable combinational block that
  is essentially universally applicable to any RISC-V core where decode happens
  within a single stage. It is well-verified and supports all of the RISC-V
  standard extensions in their latest incarnations.

* The RISC-V compressed (RVC) expander is also implemented as a reusable
  combinational block.  Because every RVC instruction maps onto a normal 32-bit
  encoding, this universal expander handles all of the RVC extension, aside from
  the extra complication of designing fetch logic to handle 16-bit aligned
  program counters.

* The CSR file implements essentially all of the privileged architecture as a
  state machine.

* Building around Rocket components allows BottleRocket to be a fully
  spec-compliant RV32IMC core with machine and user modes while occupying a few
  hundred lines of total new Chisel code.

## Building and Running

The first step to using BottleRocket is making sure that the work environment is
ready to support RISC-V development. It is helpful to follow the convention that
the RISCV environment variable points to the RISC-V toolchain installation.

1. Add the following to your environment using configuration files and/or a
   script

   ```bash
   $ export RISCV=<desired path>
   $ export PATH=$PATH:$RISCV/bin
   ```

2. Clone and install the RV32IMC toolchain. Note, this requires changing the
   meta-build script that calls configure and make in each process, as shown
   with the sed invocation below.

   ```bash
   $ git clone https://github.com/riscv/riscv-tools
   $ cd riscv-tools
   $ sed 's/ima/imc/g' <build-rv32ima.sh >build-rv32imc.sh
   $ chmod +x build-rv32imc.sh
   $ ./build-rv32imc.sh
   ```

3. Enter the BottleRocket directory and run the standalone tests. NOTE: you may
   need to modify `test/Makefile` to target an appropriate Verilog simulator for
   your environment.

   ```bash
   $ cd <path to BottleRocket top dir>
   $ make test
   ```

4. The generated Verilog is in `generated-src/BottleRocketCore.v` -- this
   contains the top level BottleRocketCore module that can be instantiated in a
   Verilog design.


5. Try running sbt (“Simple Build Tool,” the most popular build tool for Scala
   projects) manually. This allows more options for building the core. The
   following command will print all available command-line options (number of
   interrupt lines, target directory for Verilog generation, etc.).

   ```bash
   $ sbt "runMain bottlerocket.BottleRocketGenerator --help"
   ```
