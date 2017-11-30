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
`include "MockAXI4LiteSRAM.v"

`define MAXCYCLES 100000
`define TOHOSTADDR 'h00006000
`define SUCCESSCODE 1

module BottleRocketCoreTB(
                          );

   reg           clk;
   reg           reset;

   wire          imem_awvalid;
   wire          imem_awready;
   wire [31:0]   imem_awaddr;
   wire [2:0]    imem_awprot;
   wire [3:0]    imem_awcache;
   wire          imem_wvalid;
   wire          imem_wready;
   wire [31:0]   imem_wdata;
   wire [3:0]    imem_wstrb;
   wire          imem_bvalid;
   wire          imem_bready;
   wire [1:0]    imem_bresp;
   wire          imem_arvalid;
   wire          imem_arready;
   wire [31:0]   imem_araddr;
   wire [2:0]    imem_arprot;
   wire [3:0]    imem_arcache;
   wire          imem_rvalid;
   wire          imem_rready;
   wire [1:0]    imem_rresp;
   wire [31:0]   imem_rdata;


   wire          dmem_awvalid;
   wire          dmem_awready;
   wire [31:0]   dmem_awaddr;
   wire [2:0]    dmem_awprot;
   wire [3:0]    dmem_awcache;
   wire          dmem_wvalid;
   wire          dmem_wready;
   wire [31:0]   dmem_wdata;
   wire [3:0]    dmem_wstrb;
   wire          dmem_bvalid;
   wire          dmem_bready;
   wire [1:0]    dmem_bresp;
   wire          dmem_arvalid;
   wire          dmem_arready;
   wire [31:0]   dmem_araddr;
   wire [2:0]    dmem_arprot;
   wire [3:0]    dmem_arcache;
   wire          dmem_rvalid;
   wire          dmem_rready;
   wire [1:0]    dmem_rresp;
   wire [31:0]   dmem_rdata;

   MockAXI4LiteSRAM imem(
		         .aclk(clk),
		         .aresetn(~reset),
                         .awvalid(imem_awvalid),
                         .awready(imem_awready),
                         .awaddr(imem_awaddr),
                         .awprot(imem_awprot),
                         .awcache(imem_awcache),
                         .wvalid(imem_wvalid),
                         .wready(imem_wready),
                         .wdata(imem_wdata),
                         .wstrb(imem_wstrb),
                         .bvalid(imem_bvalid),
                         .bready(imem_bready),
                         .bresp(imem_bresp),
                         .arvalid(imem_arvalid),
                         .arready(imem_arready),
                         .araddr(imem_araddr),
                         .arprot(imem_arprot),
                         .arcache(imem_arcache),
                         .rvalid(imem_rvalid),
                         .rready(imem_rready),
                         .rresp(imem_rresp),
                         .rdata(imem_rdata)
		         );

   MockAXI4LiteSRAM dmem(
		         .aclk(clk),
		         .aresetn(~reset),
                         .awvalid(dmem_awvalid),
                         .awready(dmem_awready),
                         .awaddr(dmem_awaddr),
                         .awprot(dmem_awprot),
                         .awcache(dmem_awcache),
                         .wvalid(dmem_wvalid),
                         .wready(dmem_wready),
                         .wdata(dmem_wdata),
                         .wstrb(dmem_wstrb),
                         .bvalid(dmem_bvalid),
                         .bready(dmem_bready),
                         .bresp(dmem_bresp),
                         .arvalid(dmem_arvalid),
                         .arready(dmem_arready),
                         .araddr(dmem_araddr),
                         .arprot(dmem_arprot),
                         .arcache(dmem_arcache),
                         .rvalid(dmem_rvalid),
                         .rready(dmem_rready),
                         .rresp(dmem_rresp),
                         .rdata(dmem_rdata)
		         );

   BottleRocketCore core(
                         .clock(clk),
                         .reset(reset),
                         .io_constclk(clk),
                         .io_nmi(1'b0),
                         .io_eip(1'b0),
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
                         .io_iBus_aw_valid(imem_awvalid),
                         .io_iBus_aw_ready(imem_awready),
                         .io_iBus_aw_bits_addr(imem_awaddr),
                         .io_iBus_aw_bits_prot(imem_awprot),
                         .io_iBus_aw_bits_cache(imem_awcache),
                         .io_iBus_w_valid(imem_wvalid),
                         .io_iBus_w_ready(imem_wready),
                         .io_iBus_w_bits_data(imem_wdata),
                         .io_iBus_w_bits_strb(imem_wstrb),
                         .io_iBus_b_valid(imem_bvalid),
                         .io_iBus_b_ready(imem_bready),
                         .io_iBus_b_bits_resp(imem_bresp),
                         .io_iBus_ar_valid(imem_arvalid),
                         .io_iBus_ar_ready(imem_arready),
                         .io_iBus_ar_bits_addr(imem_araddr),
                         .io_iBus_ar_bits_prot(imem_arprot),
                         .io_iBus_ar_bits_cache(imem_arcache),
                         .io_iBus_r_valid(imem_rvalid),
                         .io_iBus_r_ready(imem_rready),
                         .io_iBus_r_bits_resp(imem_rresp),
                         .io_iBus_r_bits_data(imem_rdata),
                         .io_dBus_aw_valid(dmem_awvalid),
                         .io_dBus_aw_ready(dmem_awready),
                         .io_dBus_aw_bits_addr(dmem_awaddr),
                         .io_dBus_aw_bits_prot(dmem_awprot),
                         .io_dBus_aw_bits_cache(dmem_awcache),
                         .io_dBus_w_valid(dmem_wvalid),
                         .io_dBus_w_ready(dmem_wready),
                         .io_dBus_w_bits_data(dmem_wdata),
                         .io_dBus_w_bits_strb(dmem_wstrb),
                         .io_dBus_b_valid(dmem_bvalid),
                         .io_dBus_b_ready(dmem_bready),
                         .io_dBus_b_bits_resp(dmem_bresp),
                         .io_dBus_ar_valid(dmem_arvalid),
                         .io_dBus_ar_ready(dmem_arready),
                         .io_dBus_ar_bits_addr(dmem_araddr),
                         .io_dBus_ar_bits_prot(dmem_arprot),
                         .io_dBus_ar_bits_cache(dmem_arcache),
                         .io_dBus_r_valid(dmem_rvalid),
                         .io_dBus_r_ready(dmem_rready),
                         .io_dBus_r_bits_resp(dmem_rresp),
                         .io_dBus_r_bits_data(dmem_rdata)
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
      if (dmem_awvalid && dmem_wvalid && dmem_awaddr == `TOHOSTADDR) begin
         if (dmem_wdata == `SUCCESSCODE) begin
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
