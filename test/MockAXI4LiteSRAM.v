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

// Mock AXI4Lite SRAM for unit testing the BottleRocket core

module MockAXI4LiteSRAM (
		input               aclk,
		input               aresetn,
                input               awvalid,
                output logic        awready,
                input [31:0]        awaddr,
                input [2:0]         awprot,
                input [3:0]         awcache,
                input               wvalid,
                output              wready,
                input [31:0]        wdata,
                input [3:0]         wstrb,
                output logic        bvalid,
                input               bready,
                output [1:0]        bresp,
                input               arvalid,
                output logic        arready,
                input [31:0]        araddr,
                input [2:0]         arprot,
                input [3:0]         arcache,
                output logic        rvalid,
                input               rready,
                output [1:0]        rresp,
                output logic [31:0] rdata
		);

   logic [31:0]               mem [0:32767];

   logic                      write_active;
   logic [3:0]                write_resp_delay;
   logic [3:0]                write_strb;
   logic [31:0]               write_data;
   logic [31:0]               write_addr;

   logic                      read_active;
   logic [3:0]                read_resp_delay;
   logic [31:0]               read_data;
   logic [31:0]               read_addr;
   
   // Writes
   // TODO (amagyar): handle awvalid / wvalid independently

   always @(posedge aclk) begin
      if (awvalid && awready && wvalid && wready) begin
         write_resp_delay <= $random;
      end else if (write_active && write_resp_delay != 0) begin
         write_resp_delay <= write_resp_delay - 1;
      end
   end

   always @(posedge aclk) begin
      if (!aresetn) begin
	 write_active <= 1'b0;
      end else if (awvalid && awready && wvalid && wready) begin
	 write_active <= 1'b1;
      end else if (bvalid && bready) begin
	 write_active <= 1'b0;
      end
   end // always @ (posedge aclk)

   always @(posedge aclk) begin
      if (awvalid && awready && wvalid && wready) begin
         if (wstrb[0])
           mem[awaddr[31:2]][7:0] <= wdata[7:0];
         if (wstrb[1])
           mem[awaddr[31:2]][15:8] <= wdata[15:8];
         if (wstrb[2])
           mem[awaddr[31:2]][23:16] <= wdata[23:16];
         if (wstrb[3])
           mem[awaddr[31:2]][31:24] <= wdata[31:24];
      end
   end

   assign awready = !write_active || (bready && bvalid);
   assign wready = !write_active || (bready && bvalid);
   assign bvalid = write_active && write_resp_delay == 0;
   assign bresp = 2'b0;

   // Reads

   always @(posedge aclk) begin
      if (arvalid && arready) begin
         read_resp_delay <= $random;
      end else if (read_active && read_resp_delay != 0) begin
         read_resp_delay <= read_resp_delay - 1;
      end
   end
   
   always @(posedge aclk) begin
      if (!aresetn) begin
	 read_active <= 1'b0;
      end else if (arvalid && arready) begin
	 read_active <= 1'b1;
	 read_addr <= araddr;
      end else if (rvalid && rready) begin
	 read_active <= 1'b0;
      end
   end // always @ (posedge aclk)

   always @(*) begin
      rdata = mem[read_addr[31:2]];
   end

   assign arready = !read_active || (rready && rvalid);
   assign rvalid = read_active && read_resp_delay == 0;
   assign rresp = 2'b0;

endmodule // MockAXI4LiteSRAM
