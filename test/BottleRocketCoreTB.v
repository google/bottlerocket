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

// Unit-level testbench for the BottleRocket core with RISC-V isa tests

`include "BottleRocketCore.v"
`include "GBXSRAM.v"

`define MAXCYCLES 100000
`define TOHOSTADDR 'h00006000
`define SUCCESSCODE 1

module BottleRocketCoreTB(
                          );

   reg           clk;
   reg           reset;

   wire          iBus_greqvalid;
   wire          iBus_greqwrite;
   wire [31:0]   iBus_greqaddr;
   wire [3:0]    iBus_greqlen;
   wire [15:0]   iBus_greqid;
   wire          iBus_greqdvalid;
   wire [31:0]   iBus_greqdata;
   wire [1:0]    iBus_greqsize;
   wire          iBus_greqdlast;
   wire [15:0]   iBus_grequser;
   wire          iBus_greqready;
   wire          iBus_grspvalid;
   wire [31:0]   iBus_grspdata;
   wire          iBus_grspwerr;
   wire          iBus_grsprerr;
   wire [15:0]   iBus_grspid;
   wire          iBus_grsplast;
   wire [15:0]   iBus_grspuser;
   wire          iBus_grspready;

   wire          dBus_greqvalid;
   wire          dBus_greqwrite;
   wire [31:0]   dBus_greqaddr;
   wire [3:0]    dBus_greqlen;
   wire [15:0]   dBus_greqid;
   wire          dBus_greqdvalid;
   wire [31:0]   dBus_greqdata;
   wire [1:0]    dBus_greqsize;
   wire          dBus_greqdlast;
   wire [15:0]   dBus_grequser;
   wire          dBus_greqready;
   wire          dBus_grspvalid;
   wire [31:0]   dBus_grspdata;
   wire          dBus_grspwerr;
   wire          dBus_grsprerr;
   wire [15:0]   dBus_grspid;
   wire          dBus_grsplast;
   wire [15:0]   dBus_grspuser;
   wire          dBus_grspready;

   wire          sBus_greqvalid;
   wire          sBus_greqwrite;
   wire [31:0]   sBus_greqaddr;
   wire [3:0]    sBus_greqlen;
   wire [15:0]   sBus_greqid;
   wire          sBus_greqdvalid;
   wire [31:0]   sBus_greqdata;
   wire [1:0]    sBus_greqsize;
   wire          sBus_greqdlast;
   wire [15:0]   sBus_grequser;
   wire          sBus_greqready;
   wire          sBus_grspvalid;
   wire [31:0]   sBus_grspdata;
   wire          sBus_grspwerr;
   wire          sBus_grsprerr;
   wire [15:0]   sBus_grspid;
   wire          sBus_grsplast;
   wire [15:0]   sBus_grspuser;
   wire          sBus_grspready;

   BottleRocketCore core(
                         .clock(clk),
                         .reset(reset),
                         .io_constclk(clk),
                         .io_iBus_gclk(clk),
                         .io_nmi(1'b0),
                         .io_interruptLines(240'b0),
                         .io_dmi_req_ready(),
                         .io_dmi_req_valid(1'b0),
                         .io_dmi_req_bits_addr(7'b0),
                         .io_dmi_req_bits_data(32'b0),
                         .io_dmi_req_bits_op(2'b0),
                         .io_dmi_resp_ready(1'b0),
                         .io_dmi_resp_valid(),
                         .io_dmi_resp_bits_data(),
                         .io_dmi_resp_bits_resp(),
                         .io_wfisleep(),
                         .io_traceInst(),
                         .io_traceRetire(),
                         .io_traceInterrupt(),
                         .io_traceEret(),
                         .io_iBus_greqvalid(iBus_greqvalid),
                         .io_iBus_greqwrite(iBus_greqwrite),
                         .io_iBus_greqaddr(iBus_greqaddr),
                         .io_iBus_greqlen(iBus_greqlen),
                         .io_iBus_greqid(iBus_greqid),
                         .io_iBus_greqdvalid(iBus_greqdvalid),
                         .io_iBus_greqdata(iBus_greqdata),
                         .io_iBus_greqsize(iBus_greqsize),
                         .io_iBus_greqdlast(iBus_greqdlast),
                         .io_iBus_grequser(iBus_grequser),
                         .io_iBus_greqready(iBus_greqready),
                         .io_iBus_grspvalid(iBus_grspvalid),
                         .io_iBus_grspdata(iBus_grspdata),
                         .io_iBus_grspwerr(iBus_grspwerr),
                         .io_iBus_grsprerr(iBus_grsprerr),
                         .io_iBus_grspid(iBus_grspid),
                         .io_iBus_grsplast(iBus_grsplast),
                         .io_iBus_grspuser(iBus_grspuser),
                         .io_iBus_grspready(iBus_grspready),
                         .io_dBus_greqvalid(dBus_greqvalid),
                         .io_dBus_greqwrite(dBus_greqwrite),
                         .io_dBus_greqaddr(dBus_greqaddr),
                         .io_dBus_greqlen(dBus_greqlen),
                         .io_dBus_greqid(dBus_greqid),
                         .io_dBus_greqdvalid(dBus_greqdvalid),
                         .io_dBus_greqdata(dBus_greqdata),
                         .io_dBus_greqsize(dBus_greqsize),
                         .io_dBus_greqdlast(dBus_greqdlast),
                         .io_dBus_grequser(dBus_grequser),
                         .io_dBus_greqready(dBus_greqready),
                         .io_dBus_grspvalid(dBus_grspvalid),
                         .io_dBus_grspdata(dBus_grspdata),
                         .io_dBus_grspwerr(dBus_grspwerr),
                         .io_dBus_grsprerr(dBus_grsprerr),
                         .io_dBus_grspid(dBus_grspid),
                         .io_dBus_grsplast(dBus_grsplast),
                         .io_dBus_grspuser(dBus_grspuser),
                         .io_dBus_grspready(dBus_grspready),
                         .io_sBus_greqvalid(sBus_greqvalid),
                         .io_sBus_greqwrite(sBus_greqwrite),
                         .io_sBus_greqaddr(sBus_greqaddr),
                         .io_sBus_greqlen(sBus_greqlen),
                         .io_sBus_greqid(sBus_greqid),
                         .io_sBus_greqdvalid(sBus_greqdvalid),
                         .io_sBus_greqdata(sBus_greqdata),
                         .io_sBus_greqsize(sBus_greqsize),
                         .io_sBus_greqdlast(sBus_greqdlast),
                         .io_sBus_grequser(sBus_grequser),
                         .io_sBus_greqready(sBus_greqready),
                         .io_sBus_grspvalid(sBus_grspvalid),
                         .io_sBus_grspdata(sBus_grspdata),
                         .io_sBus_grspwerr(sBus_grspwerr),
                         .io_sBus_grsprerr(sBus_grsprerr),
                         .io_sBus_grspid(sBus_grspid),
                         .io_sBus_grsplast(sBus_grsplast),
                         .io_sBus_grspuser(sBus_grspuser),
                         .io_sBus_grspready(sBus_grspready),
                         .io_localBus_greqvalid(),
                         .io_localBus_greqwrite(),
                         .io_localBus_greqaddr(),
                         .io_localBus_greqlen(),
                         .io_localBus_greqid(),
                         .io_localBus_greqdvalid(),
                         .io_localBus_greqdata(),
                         .io_localBus_greqsize(),
                         .io_localBus_greqdlast(),
                         .io_localBus_grequser(),
                         .io_localBus_greqready(1'b0),
                         .io_localBus_grspvalid(1'b0),
                         .io_localBus_grspdata(32'b0),
                         .io_localBus_grspwerr(1'b0),
                         .io_localBus_grsprerr(1'b0),
                         .io_localBus_grspid(16'b0),
                         .io_localBus_grsplast(1'b0),
                         .io_localBus_grspuser(16'b0),
                         .io_localBus_grspready()
                         );

   GBXSRAM imem(
                .clk(clk),
                .reset(reset),
                .greqvalid(iBus_greqvalid),
                .greqwrite(iBus_greqwrite),
                .greqaddr(iBus_greqaddr),
                .greqlen(iBus_greqlen),
                .greqid(iBus_greqid),
                .greqdvalid(iBus_greqdvalid),
                .greqdata(iBus_greqdata),
                .greqsize(iBus_greqsize),
                .greqdlast(iBus_greqdlast),
                .grequser(iBus_grequser),
                .greqready(iBus_greqready),
                .grspvalid(iBus_grspvalid),
                .grspdata(iBus_grspdata),
                .grspwerr(iBus_grspwerr),
                .grsprerr(iBus_grsprerr),
                .grspid(iBus_grspid),
                .grsplast(iBus_grsplast),
                .grspuser(iBus_grspuser),
                .grspready(iBus_grspready)
                );

   GBXSRAM dmem(
                .clk(clk),
                .reset(reset),
                .greqvalid(dBus_greqvalid),
                .greqwrite(dBus_greqwrite),
                .greqaddr(dBus_greqaddr),
                .greqlen(dBus_greqlen),
                .greqid(dBus_greqid),
                .greqdvalid(dBus_greqdvalid),
                .greqdata(dBus_greqdata),
                .greqsize(dBus_greqsize),
                .greqdlast(dBus_greqdlast),
                .grequser(dBus_grequser),
                .greqready(dBus_greqready),
                .grspvalid(dBus_grspvalid),
                .grspdata(dBus_grspdata),
                .grspwerr(dBus_grspwerr),
                .grsprerr(dBus_grsprerr),
                .grspid(dBus_grspid),
                .grsplast(dBus_grsplast),
                .grspuser(dBus_grspuser),
                .grspready(dBus_grspready)
                );

   GBXSRAM smem(
                .clk(clk),
                .reset(reset),
                .greqvalid(sBus_greqvalid),
                .greqwrite(sBus_greqwrite),
                .greqaddr(sBus_greqaddr),
                .greqlen(sBus_greqlen),
                .greqid(sBus_greqid),
                .greqdvalid(sBus_greqdvalid),
                .greqdata(sBus_greqdata),
                .greqsize(sBus_greqsize),
                .greqdlast(sBus_greqdlast),
                .grequser(sBus_grequser),
                .greqready(sBus_greqready),
                .grspvalid(sBus_grspvalid),
                .grspdata(sBus_grspdata),
                .grspwerr(sBus_grspwerr),
                .grsprerr(sBus_grsprerr),
                .grspid(sBus_grspid),
                .grsplast(sBus_grsplast),
                .grspuser(sBus_grspuser),
                .grspready(sBus_grspready)
                );

   integer       ncycles;

   reg [1023:0]  image;

   initial begin
      if ($value$plusargs("image=%s", image)) begin
         $readmemh({image,".hex"}, imem.mem);
         $readmemh({image,".hex"}, dmem.mem);
         $shm_open({image,".dump.d"});
      end else begin
         $fatal;
      end
      $shm_probe("AMC");
      $recordvars(core);
      ncycles = 0;
      clk = 1'b0;
      reset = 1'b1;
      repeat(20) #5 clk = ~clk;
      reset = 1'b0;
      forever #5 clk = ~clk;
   end

   always @(posedge clk) begin
      ncycles = ncycles + 1;
      if (ncycles > `MAXCYCLES) begin
         $info("Failure: timeout!\n");
         $shm_close;
         $fatal;
      end
   end

   always @(posedge clk) begin
      if (dBus_greqvalid && dBus_greqwrite && dBus_greqaddr == `TOHOSTADDR) begin
         if (dBus_greqdata == `SUCCESSCODE) begin
            $info("Success!\n");
            $shm_close;
            $finish;
         end else begin
            $info("Failure!\n");
            $shm_close;
            $fatal;
         end
      end
   end

endmodule
