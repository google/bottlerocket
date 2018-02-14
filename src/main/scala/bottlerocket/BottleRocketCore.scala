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

// Top-level pipeline for the BottleRocket core

package bottlerocket

import chisel3._
import chisel3.util.{RegEnable}
import chisel3.core.withClock
import freechips.rocketchip._
import rocket._
import devices.debug.DMIIO
import amba.axi4.{AXI4Bundle, AXI4Parameters}
import Params._

class BottleRocketCore(options: BROptions)(implicit p: config.Parameters) extends Module {
  val io = IO(new Bundle {
    val constclk = Input(Clock())
    val iBus = AXI4LiteBundle(axiParams)
    val dBus = AXI4LiteBundle(axiParams)
    val nmi = Input(Bool())
    val eip = Input(Bool())
    val dmi = Flipped(new DMIIO()(p))
    val wfisleep = Output(Bool())
    val traceInst = Output(UInt(width = xBitwidth))
    val traceRetire = Output(Bool())
    val traceInterrupt = Output(Bool())
    val traceEret = Output(Bool())
    val rvfi = new RVFI
  })

  /********************
   * MAJOR SUBMODULES *
   ********************/

  val fetchMgr = Module(new FrontendBuffer(options))
  val regfile = new RegfileWithZero(nRegisters, xBitwidth)
  val alu = Module(new ALU())
  val debug = Module(new DebugModuleFSM())
  val csrfile = Module(new CSRFile())
  val breakpoints = Module(new BreakpointUnit(csrfile.nBreakpoints))

  /***************
   * WFISLEEP FF *
   ***************/

  // No need to wait for outstanding IBus request; manager is designed for this case
  val s_ready :: s_sleeping :: s_waking :: Nil = chisel3.util.Enum(UInt(), 3)
  val sleepstate = withClock(io.constclk) { Reg(init = s_ready) }

  fetchMgr.io.sleeping := sleepstate =/= s_ready
  io.wfisleep := sleepstate === s_sleeping && !fetchMgr.io.outstanding

  when (sleepstate === s_ready) {
    when (csrfile.io.csr_stall) {
      sleepstate := s_sleeping
    }
  } .elsewhen (sleepstate === s_sleeping) {
    when (io.eip) {
      sleepstate := s_waking
    }
  } .otherwise {
    when (!csrfile.io.csr_stall) {
      sleepstate := s_ready
    }
  }

  /******************************
   * IMPRECISE STORE INTERRUPTS *
   ******************************/

  val impreciseStoreInterrupt = Reg(init = Bool(false))

  /*******************************
   * HAZARD AND REDIRECT SIGNALS *
   *******************************/

  // Hazard and redirect signals
  val wait_IF = Wire(Bool())
  val stall_IF = Wire(Bool())
  val kill_IF = Wire(Bool())
  val stall_EX = Wire(Bool())
  val kill_EX = Wire(Bool())
  val stall_WB = Wire(Bool())
  val kill_WB = Wire(Bool())
  val exception_WB = Wire(Bool())
  val nextPC = Wire(UInt(width = xBitwidth))

  val busReqWait_EX = Wire(Bool())
  val mulDivReqWait_EX = Wire(Bool())
  val busRespWait_WB = Wire(Bool())
  val mulDivRespWait_WB = Wire(Bool())

  // Memory kill signals
  val memStructuralHazard = Wire(Bool())
  val killMem_EX = Wire(Bool())

  /**********************
   * PIPELINE REGISTERS *
   **********************/

  val canonicalNOP = UInt(0x13, width = instBitwidth)

  val resetHold = Reg(init = Bool(true))

  val PC_IF = Wire(UInt(width = xBitwidth))

  val PC_EX = Reg(init = UInt(0, width = xBitwidth))
  val instPreExp_EX = Reg(init = canonicalNOP)
  val pastExceptions_EX = Reg(init = ExceptionCause.clear)
  val isBubble_EX = Reg(init = Bool(true))

  val isBubble_WB = Reg(init = Bool(true))
  val PC_WB = Reg(init = UInt(0, width = xBitwidth))
  val rvc_WB = Reg(init = Bool(false))
  val pastExceptions_WB = Reg(init = ExceptionCause.clear)
  val jal_WB = Reg(init = Bool(false))
  val jalr_WB = Reg(init = Bool(false))
  val memEn_WB = Reg(init = Bool(false))
  val memAddrOffset_WB = Reg(init = UInt(0, width = xByteOffsetBitwidth))
  val isStore_WB = Reg(init = Bool(false))
  val memType_WB = Reg(init = UInt(0, width = MT_SZ))
  val writeReg_WB = Reg(init = Bool(false))
  val rdAddr_WB = Reg(init = UInt(0))
  val csrCmd_WB = Reg(init = CSR.N)
  val mulDiv_WB = Reg(init = Bool(false))
  val aluOut_WB = Reg(init = UInt(0))
  val inst_WB = Reg(init = canonicalNOP)

  /***************************
   * INSTRUCTION FETCH STAGE *
   ***************************/

  PC_IF := fetchMgr.io.resp.bits.pc
  val instPreExp_IF = fetchMgr.io.resp.bits.inst
  val rvc_IF = instPreExp_IF(1,0) =/= UInt(3)
  val exceptions_IF = Wire(new ExceptionCause)
  exceptions_IF := ExceptionCause.clear

  fetchMgr.io.req.enter_U_mode := csrfile.io.eret && !csrfile.io.status.debug &&
  csrfile.io.status.mpp === UInt(PRV.U)
  fetchMgr.io.req.exit_U_mode := csrfile.io.exception && !(csrfile.io.cause === UInt(CSR.debugTriggerCause))
  fetchMgr.io.req.pc := nextPC
  fetchMgr.io.resp.ready := !stall_IF
  io.iBus <> fetchMgr.io.bus

  val busWerr = io.dBus.b.valid && (io.dBus.b.bits.resp === AXI4Parameters.RESP_SLVERR || io.dBus.b.bits.resp === AXI4Parameters.RESP_DECERR)
  val werrExceptionTaken = exception_WB && csrfile.io.cause === UInt(Causes.store_access)
  when (busWerr) {
    impreciseStoreInterrupt := Bool(true)
  } .elsewhen(werrExceptionTaken) {
    impreciseStoreInterrupt := Bool(false)
  }

  exceptions_IF.interrupt := csrfile.io.interrupt
  exceptions_IF.storeFault := impreciseStoreInterrupt
  exceptions_IF.misalignedFetch := fetchMgr.io.resp.valid && PC_IF(0)
  exceptions_IF.illegalFetch := fetchMgr.io.resp.valid && fetchMgr.io.resp.bits.error

  /************************
   * DECODE/EXECUTE STAGE *
   ************************/

  when (!stall_EX) {
    PC_EX := PC_IF
    instPreExp_EX := instPreExp_IF
    pastExceptions_EX := exceptions_IF
    isBubble_EX := kill_IF
  }

  val rvc_EX = instPreExp_EX(1,0) =/= UInt(3)
  val instExp_EX = new RVCDecoder(instPreExp_EX).decode
  val inst_EX = instExp_EX.bits

  val decoder_data = Seq(new MDecode, new IDecode).flatMap(_.table)
  val ctrl_EX = Wire(new IntCtrlSigs()).decode(inst_EX, decoder_data)
  val memEn_EX = ctrl_EX.mem && (ctrl_EX.mem_cmd === M_XWR || ctrl_EX.mem_cmd === M_XRD)
  val isLoad_EX = ctrl_EX.mem && ctrl_EX.mem_cmd === M_XRD
  val isStore_EX = ctrl_EX.mem && ctrl_EX.mem_cmd === M_XWR

  val rdAddr_EX = instExp_EX.rd
  val rs1Addr_EX = instExp_EX.rs1
  val rs2Addr_EX = instExp_EX.rs2
  val shamt_EX = inst_EX(24, 20)
  val imm_EX = ImmGen(ctrl_EX.sel_imm, inst_EX)

  val csrAccess_EX = (ctrl_EX.csr === CSR.S) || (ctrl_EX.csr === CSR.C) || (ctrl_EX.csr === CSR.W)
  val csrRead_EX = rs1Addr_EX === UInt(0) && (ctrl_EX.csr =/= CSR.N)
  val csrCmd_EX = Mux(csrRead_EX, CSR.R, ctrl_EX.csr)

  val rs1AddrDebugMuxed_EX = Mux(csrfile.io.status.debug, debug.io.gpraddr, rs1Addr_EX)
  val rs1Data_EX = regfile.read(rs1AddrDebugMuxed_EX)
  val rs2Data_EX = regfile.read(rs2Addr_EX)

  val rdReady_WB = !(memEn_WB || mulDiv_WB) && (csrCmd_WB === CSR.N)
  val writing_WB = !isBubble_WB && writeReg_WB && rdAddr_WB =/= UInt(0)
  val rs1RAW_EX = writing_WB && !isBubble_EX && ctrl_EX.rxs1 && rs1Addr_EX === rdAddr_WB
  val rs2RAW_EX = writing_WB && !isBubble_EX && ctrl_EX.rxs2 && rs2Addr_EX === rdAddr_WB
  val rs1BypassALUResult_EX = rs1RAW_EX && rdReady_WB
  val rs2BypassALUResult_EX = rs2RAW_EX && rdReady_WB
  val unbypassable_EX = (rs1RAW_EX || rs2RAW_EX) && !rdReady_WB
  val rs1DataBypassed_EX = Mux(rs1BypassALUResult_EX,aluOut_WB,rs1Data_EX)
  val rs2DataBypassed_EX = Mux(rs2BypassALUResult_EX,aluOut_WB,rs2Data_EX)

  val arg1_EX = Wire(SInt(width = xBitwidth))
  when (ctrl_EX.sel_alu1 === A1_RS1) {
    arg1_EX := rs1DataBypassed_EX.asSInt
  } .elsewhen (ctrl_EX.sel_alu1 === A1_PC) {
    arg1_EX := PC_EX.asSInt
  } .otherwise {
    arg1_EX := SInt(0)
  }

  val arg2_EX = Wire(SInt(width = xBitwidth))
  when (ctrl_EX.sel_alu2 === A2_RS2) {
    arg2_EX := rs2DataBypassed_EX.asSInt
  } .elsewhen (ctrl_EX.sel_alu2 === A2_IMM) {
    arg2_EX := imm_EX
  } .elsewhen (ctrl_EX.sel_alu2 === A2_SIZE) {
    arg2_EX := Mux(rvc_EX,SInt(2),SInt(4))
  } .otherwise {
    arg2_EX := SInt(0)
  }

  alu.io.fn := ctrl_EX.alu_fn
  alu.io.dw := DW_32
  alu.io.in1 := arg1_EX.asUInt
  alu.io.in2 := arg2_EX.asUInt

  val memSize_EX = Wire(UInt())
  val wdata_EX = Wire(UInt(width = xBitwidth))
  when (ctrl_EX.mem_type === MT_B || ctrl_EX.mem_type === MT_BU) {
    memSize_EX := UInt(1)
    wdata_EX := chisel3.util.Cat(Seq.fill(4){ rs2DataBypassed_EX(7,0) })
  } .elsewhen (ctrl_EX.mem_type === MT_H || ctrl_EX.mem_type === MT_HU) {
    memSize_EX := UInt(2)
    wdata_EX := chisel3.util.Cat(Seq.fill(2){ rs2DataBypassed_EX(15,0) })
  } .otherwise {
    memSize_EX := UInt(4)
    wdata_EX := rs2DataBypassed_EX
  }

  csrfile.io.decode.csr := inst_EX(31,20)
  val illegalExtension = ctrl_EX.amo || ctrl_EX.fp || ctrl_EX.dp || ctrl_EX.rocc
  val illegalCSRAccess = csrfile.io.decode.read_illegal && csrAccess_EX
  val illegalCSRWrite = csrfile.io.decode.write_illegal && csrAccess_EX && !csrRead_EX
  val illegalSystem = csrfile.io.decode.system_illegal && ((ctrl_EX.mem && ctrl_EX.mem_cmd === M_SFENCE) || (ctrl_EX.csr >= CSR.I))
  val illegalInst_EX = !ctrl_EX.legal || illegalExtension || illegalCSRAccess || illegalCSRWrite || illegalSystem
  val misaligned_EX = (alu.io.adder_out & (memSize_EX - UInt(1))).orR
  val pcBreakpoint = breakpoints.io.xcpt_if
  val debugBreakpoint = breakpoints.io.debug_if
  val loadBreakpoint = isLoad_EX && breakpoints.io.xcpt_ld
  val storeBreakpoint = isStore_EX && breakpoints.io.xcpt_st
  val loadDebugBreakpoint = isLoad_EX && breakpoints.io.debug_ld
  val storeDebugBreakpoint = isStore_EX && breakpoints.io.debug_st

  val exceptions_EX = Wire(new ExceptionCause)
  exceptions_EX := pastExceptions_EX
  exceptions_EX.illegalInstruction := illegalInst_EX
  exceptions_EX.breakpoint := pcBreakpoint || loadBreakpoint || storeBreakpoint
  exceptions_EX.debugBreakpoint := debugBreakpoint || loadDebugBreakpoint || storeDebugBreakpoint
  exceptions_EX.loadMisaligned := misaligned_EX && isLoad_EX
  exceptions_EX.storeMisaligned := misaligned_EX && isStore_EX

  // Do not access memory with an unhandled exception!
  val memEnMasked_EX = memEn_EX && !killMem_EX && !ExceptionCause.toBool(exceptions_EX)
  val awSentWUnsent_EX = RegInit(Bool(false))
  when (io.dBus.aw.fire && !io.dBus.w.fire) {
    awSentWUnsent_EX := Bool(true)
  } .elsewhen (io.dBus.w.fire && !io.dBus.aw.fire) {
    awSentWUnsent_EX := Bool(false)
  }

  io.dBus.ar.valid := memEnMasked_EX && isLoad_EX
  io.dBus.ar.bits.addr := alu.io.adder_out.asUInt
  io.dBus.ar.bits.cache := AXI4Parameters.CACHE_BUFFERABLE
  io.dBus.ar.bits.prot := Mux(csrfile.io.status.prv === UInt(PRV.M), AXI4Parameters.PROT_PRIVILEDGED, AXI4Parameters.PROT_INSECURE)

  io.dBus.aw.valid := memEnMasked_EX && isStore_EX && !awSentWUnsent_EX
  io.dBus.aw.bits.addr := alu.io.adder_out.asUInt
  io.dBus.aw.bits.cache := AXI4Parameters.CACHE_BUFFERABLE
  io.dBus.aw.bits.prot := Mux(csrfile.io.status.prv === UInt(PRV.M), AXI4Parameters.PROT_PRIVILEDGED, AXI4Parameters.PROT_INSECURE)

  io.dBus.w.valid := memEnMasked_EX && isStore_EX
  io.dBus.w.bits.data := wdata_EX
  io.dBus.w.bits.strb := ((1.U << memSize_EX) - 1.U) << io.dBus.aw.bits.addr(1,0)

  // Multiplier/Divider
  val mulDivEnMasked_EX = !kill_EX && ctrl_EX.div
  val mulDiv = Module(new MulDiv(cfg = MulDivParams(), width = xBitwidth.get))
  mulDiv.io.req.valid := mulDivEnMasked_EX
  mulDiv.io.req.bits.dw := ctrl_EX.alu_dw
  mulDiv.io.req.bits.fn := ctrl_EX.alu_fn
  mulDiv.io.req.bits.in1 := rs1DataBypassed_EX
  mulDiv.io.req.bits.in2 := rs2DataBypassed_EX
  mulDiv.io.req.bits.tag := rdAddr_EX

  breakpoints.io.pc := PC_EX
  breakpoints.io.ea := alu.io.adder_out

  /*******************
   * WRITEBACK STAGE *
   *******************/

  when (!stall_WB) {
    isBubble_WB := kill_EX || busReqWait_EX
    PC_WB := PC_EX
    rvc_WB := rvc_EX
    pastExceptions_WB := exceptions_EX
    jal_WB := ctrl_EX.jal
    jalr_WB := ctrl_EX.jalr
    writeReg_WB := ctrl_EX.wxd
    rdAddr_WB := rdAddr_EX
    memEn_WB := memEnMasked_EX
    memAddrOffset_WB := alu.io.adder_out
    isStore_WB := isStore_EX
    memType_WB := ctrl_EX.mem_type
    csrCmd_WB := ctrl_EX.csr
    mulDiv_WB := mulDivEnMasked_EX
    aluOut_WB := alu.io.out
    inst_WB := inst_EX
  }

  val writeRegMasked_WB = !kill_WB && writeReg_WB

  // No exceptions *generated in WB stage* should alter cmd, as they are system-related
  val hasPastExceptions = ExceptionCause.toBool(pastExceptions_WB)
  val csrCmdMasked_WB = Mux(isBubble_WB || hasPastExceptions, CSR.N, csrCmd_WB)

  val exceptions_WB = Wire(new ExceptionCause)
  exceptions_WB := pastExceptions_WB

  val busRerr = io.dBus.b.valid && (io.dBus.b.bits.resp === AXI4Parameters.RESP_SLVERR || io.dBus.b.bits.resp === AXI4Parameters.RESP_DECERR)
  val pendingLoadError = memEn_WB && busRerr

  // Load errors are delayed a cycle by stalling the writeback stage to avoid bus-to-bus paths
  val delayedLoadError = RegNext(next = pendingLoadError, init = Bool(false))
  exceptions_WB.loadFault := delayedLoadError

  // Always ready for bus replies
  io.dBus.r.ready := Bool(true)
  io.dBus.b.ready := Bool(true)

  // Always ready for mulDiv replies
  mulDiv.io.resp.ready := Bool(true)

  // The only time accumulated causes get ignored is with a bubble in the WB stage
  exception_WB := ExceptionCause.toBool(exceptions_WB) && !isBubble_WB

  // Only supports a single hart
  csrfile.io.hartid := UInt(0)

  // Overwrite CSR cmd/addr/wdata in debug mode
  when (csrfile.io.status.debug) {
    when (debug.io.debugRet) {
      csrfile.io.rw.cmd := CSR.I
      csrfile.io.rw.addr := "h7b2".U // dret addr
    } .otherwise {
      csrfile.io.rw.cmd := Mux(debug.io.csrwrite, CSR.W, CSR.R)
      csrfile.io.rw.addr := debug.io.csraddr
    }
    csrfile.io.rw.wdata := debug.io.regwdata
  } .otherwise {
    csrfile.io.rw.addr := inst_WB(31, 20)
    csrfile.io.rw.cmd := csrCmdMasked_WB
    csrfile.io.rw.wdata := aluOut_WB
  }

  val busErrCause = csrfile.io.cause === UInt(Causes.store_access) || csrfile.io.cause === UInt(Causes.load_access)
  csrfile.io.exception := exception_WB || io.nmi
  csrfile.io.retire := !isBubble_WB && !kill_WB
  csrfile.io.cause := ExceptionCause.toCause(exceptions_WB, csrfile.io.interrupt_cause)
  csrfile.io.pc := PC_WB
  csrfile.io.badaddr := aluOut_WB // TODO (amagyar): AXI4 store error address
  csrfile.io.fcsr_flags.valid := Bool(false)
  csrfile.io.interrupts.meip := io.eip
  csrfile.io.interrupts.mtip := Bool(false) // TODO (amagyar): support timer interrupt
  csrfile.io.interrupts.msip := Bool(false) // TODO (amagyar): support software interrupt
  csrfile.io.interrupts.lip := UInt(0).asTypeOf(csrfile.io.interrupts.lip)
  csrfile.io.rocc_interrupt := UInt(0)

  breakpoints.io.status := csrfile.io.status
  breakpoints.io.bp := csrfile.io.bp

  val loadExtender = Module(new LoadExtender)
  loadExtender.io.offset := memAddrOffset_WB
  loadExtender.io.in := io.dBus.r.bits.data
  loadExtender.io.memType := memType_WB

  val regWriteData_WB = Wire(UInt(width = xBitwidth))
  when (memEn_WB) {
    regWriteData_WB := loadExtender.io.out
  } .elsewhen (mulDiv_WB) {
    regWriteData_WB := mulDiv.io.resp.bits.data
  } .elsewhen (csrCmd_WB =/= CSR.N) {
    regWriteData_WB := csrfile.io.rw.rdata
  } .elsewhen (jalr_WB) {
    regWriteData_WB := PC_WB + Mux(rvc_WB,UInt(2),UInt(4))
  } .otherwise {
    regWriteData_WB := aluOut_WB
  }

  /********************
   * DEBUG GPR ACCESS *
   ********************/

  val regfileWriteData = Mux(csrfile.io.status.debug, debug.io.regwdata, regWriteData_WB)
  val regfileWriteAddr = Mux(csrfile.io.status.debug, debug.io.gpraddr, rdAddr_WB)

  when (writeRegMasked_WB || debug.io.gprwrite) {
    regfile.write(regfileWriteAddr, regfileWriteData)
  }

  /***********
   * TRACING *
   * *********/

  io.traceEret := csrfile.io.eret
  io.traceInterrupt := exception_WB
  io.traceRetire := csrfile.io.retire
  io.traceInst := inst_WB

  /*****************************
   * HAZARD AND REDIRECT LOGIC *
   *****************************/

  val readReqWait_EX = io.dBus.ar.valid && !io.dBus.ar.ready
  val writeReqWait_EX = (io.dBus.aw.valid && !io.dBus.aw.ready) || (io.dBus.w.valid && !io.dBus.w.ready)
  busReqWait_EX := readReqWait_EX || writeReqWait_EX
  mulDivReqWait_EX := !isBubble_EX && mulDiv.io.req.valid && !mulDiv.io.req.ready
  busRespWait_WB := !isBubble_WB && memEn_WB && ((!isStore_WB && !io.dBus.r.valid) || (isStore_WB && !io.dBus.b.valid))
  mulDivRespWait_WB := !isBubble_WB && mulDiv_WB && !mulDiv.io.resp.valid

  wait_IF := Bool(false)
  stall_IF := Bool(false)
  kill_IF := Bool(false)

  stall_EX := Bool(false)
  kill_EX := Bool(false)

  stall_WB := Bool(false)
  kill_WB := Bool(false)

  when (isBubble_EX) {
    kill_EX := Bool(true)
  }

  when (isBubble_WB) {
    kill_WB := Bool(true)
  }

  val incPC_EX = PC_EX + Mux(rvc_EX, UInt(2), UInt(4))
  val targetPC_EX = Wire(UInt(width = xBitwidth))
  val redirect_EX = !isBubble_EX && (ctrl_EX.jal || ctrl_EX.jalr || (ctrl_EX.branch && alu.io.cmp_out)) // can eliminate cmp check for constant time
  targetPC_EX := incPC_EX
  when (ctrl_EX.jalr) {
    targetPC_EX := alu.io.out ^ alu.io.out(0)
  } .elsewhen(ctrl_EX.jal ||(ctrl_EX.branch && alu.io.cmp_out)) {
    targetPC_EX := PC_EX + imm_EX.asUInt
  }

  val incAmt_IF = Mux(rvc_IF, UInt(2), UInt(4))
  when (io.nmi) {
    nextPC := UInt(p(NMI_VEC))
  } .elsewhen (exception_WB || csrfile.io.eret) {
    nextPC := csrfile.io.evec
  } .otherwise {
    nextPC := targetPC_EX
  }

  fetchMgr.io.req.redirect := exception_WB || io.nmi || csrfile.io.eret || (redirect_EX && !unbypassable_EX)
  memStructuralHazard := memEn_EX && !isBubble_EX && memEn_WB && !isBubble_WB
  killMem_EX := isBubble_EX || memStructuralHazard || exception_WB || io.nmi || csrfile.io.eret || mulDivRespWait_WB || csrfile.io.csr_stall || unbypassable_EX

  when (csrfile.io.status.debug) {
    kill_IF := Bool(true)
    kill_EX := Bool(true)
    kill_WB := Bool(true)
    stall_IF := Bool(true)
  } .elsewhen (pendingLoadError) {
    stall_IF := Bool(true)
    kill_IF := Bool(true)
    stall_EX := Bool(true)
    kill_EX := Bool(true)
    stall_WB := Bool(true)
    kill_WB := Bool(true)
  } .elsewhen (exception_WB || io.nmi) {
    kill_IF := Bool(true)
    kill_EX := Bool(true)
    kill_WB := Bool(true)
  } .elsewhen (csrfile.io.eret) {
    kill_IF := Bool(true)
    kill_EX := Bool(true)
  } .elsewhen (busRespWait_WB || mulDivRespWait_WB || csrfile.io.csr_stall) {
    stall_IF := Bool(true)
    kill_IF := Bool(true)
    stall_EX := Bool(true)
    kill_EX := Bool(true)
    stall_WB := Bool(true)
    kill_WB := Bool(true)
  } .elsewhen (memStructuralHazard || unbypassable_EX) {
    stall_IF := Bool(true)
    kill_IF := Bool(true)
    stall_EX := Bool(true)
    kill_EX := Bool(true)
  } .elsewhen (busReqWait_EX || mulDivReqWait_EX) {
    stall_IF := Bool(true)
    kill_IF := Bool(true)
    stall_EX := Bool(true)
  } .elsewhen (redirect_EX) {
    kill_IF := Bool(true)
  } .elsewhen (!fetchMgr.io.resp.valid) {
    kill_IF := Bool(true)
    wait_IF := Bool(true)
  }

  /*********
   * DEBUG *
   *********/

  val singleStepped = Reg(init = Bool(false))
  val issuing_IF = !(kill_IF || wait_IF)
  val issued = !(isBubble_EX && isBubble_WB)
  when (csrfile.io.singleStep && (issuing_IF || issued)) {
    singleStepped := Bool(true)
  } .elsewhen (csrfile.io.status.debug) {
    singleStepped := Bool(false)
  }

  debug.io.singleStepHalt := csrfile.io.status.debug && singleStepped
  debug.io.debugModeActive := csrfile.io.status.debug
  debug.io.csrrdata := csrfile.io.rw.rdata
  debug.io.gprrdata := rs1Data_EX
  csrfile.io.interrupts.debug := debug.io.debugInt || singleStepped
  when (singleStepped) {
    exceptions_IF.interrupt := Bool(true)
  }

  debug.io.dmi <> io.dmi

 /********
  * RVFI *
  ********/

  // pipeline some otherwise unnecessary signals to WB stage
  val rvfi_rs1Addr_WB = RegEnable(rs1Addr_EX, !stall_WB)
  val rvfi_rs1Data_WB = RegEnable(rs1DataBypassed_EX, !stall_WB)
  val rvfi_rs2Addr_WB = RegEnable(rs2Addr_EX, !stall_WB)
  val rvfi_rs2Data_WB = RegEnable(rs2DataBypassed_EX, !stall_WB)
  val rvfi_nextPC_WB_from_EX = RegEnable(targetPC_EX, !stall_WB)
  val rvfi_store_data_WB = RegEnable(wdata_EX, !stall_WB)
  val rvfi_retire = csrfile.io.retire === UInt(1)
  val rvfi_order = Reg(init = UInt(0, 64.W))
  when (rvfi_retire) {
    rvfi_order := rvfi_order + UInt(1)
  }

  val rvfi_next_starts_handler = Reg(init = Bool(false))
  when (exception_WB) {
    rvfi_next_starts_handler := Bool(true)
  } .elsewhen (rvfi_retire) {
    rvfi_next_starts_handler := Bool(false)
  }

  val rvfi_mem_size_mask = Wire(UInt())
  when (memEn_WB) {
    when (memType_WB === MT_B || memType_WB === MT_BU) {
      rvfi_mem_size_mask := UInt(1)
    } .elsewhen (memType_WB === MT_H || memType_WB === MT_HU) {
      rvfi_mem_size_mask := UInt(3)
    } .otherwise {
      rvfi_mem_size_mask := UInt(15)
    }
  } .otherwise {
    rvfi_mem_size_mask := UInt(0)
  }

  io.rvfi.valid := rvfi_retire
  io.rvfi.order := rvfi_order
  io.rvfi.insn := inst_WB
  io.rvfi.trap := exception_WB && (exceptions_WB.illegalInstruction || exceptions_WB.loadMisaligned || exceptions_WB.storeMisaligned)
  io.rvfi.halt := Bool(false) // TODO (amagyar): clarify halt meaning
  io.rvfi.intr := rvfi_next_starts_handler
  io.rvfi.rs1_addr := rvfi_rs1Addr_WB
  io.rvfi.rs1_rdata := rvfi_rs1Data_WB
  io.rvfi.rs2_addr := rvfi_rs2Addr_WB
  io.rvfi.rs2_rdata := rvfi_rs2Data_WB
  io.rvfi.rd_addr := Mux((writeRegMasked_WB || debug.io.gprwrite), regfileWriteAddr, UInt(0))
  io.rvfi.rd_wdata := Mux((writeRegMasked_WB || debug.io.gprwrite), regfileWriteData, UInt(0))
  io.rvfi.pc_rdata := PC_WB
  io.rvfi.pc_wdata := Mux(exception_WB || csrfile.io.eret || io.nmi, nextPC, rvfi_nextPC_WB_from_EX)
  io.rvfi.mem_addr := aluOut_WB
  io.rvfi.mem_rmask := Mux(memEn_WB && !isStore_WB, rvfi_mem_size_mask, UInt(0))
  io.rvfi.mem_rdata := Mux(memEn_WB && !isStore_WB, regfileWriteData, UInt(0))
  io.rvfi.mem_wmask := Mux(memEn_WB && isStore_WB, rvfi_mem_size_mask, UInt(0))
  io.rvfi.mem_wdata := Mux(memEn_WB && isStore_WB, rvfi_store_data_WB, UInt(0))

}
