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

// Mock GBX SRAM for unit testing

module GBXSRAM (
		input               clk,
		input               reset,
                input               greqvalid,
                input               greqwrite,
                input [31:0]        greqaddr,
                input [3:0]         greqlen,
                input [15:0]        greqid,
                input               greqdvalid,
                input [31:0]        greqdata,
                input [1:0]         greqsize,
                input               greqdlast,
                input [15:0]        grequser,
                output logic        greqready,
                output              grspvalid,
                output logic [31:0] grspdata,
                output              grspwerr,
                output              grsprerr,
                output [15:0]       grspid,
                output              grsplast,
                output [15:0]       grspuser,
                input               grspready
		);
   
   logic [31:0]               mem [0:32767];
   logic                      req_active;
   logic [15:0]               req_id;
   logic [15:0]               req_user;
   logic [31:0]               req_addr;
   logic [1:0]                req_size;
   logic                      req_is_write;
   logic [31:0]               req_data;
   

   assign greqready = !req_active || grspready;
   assign grspvalid = req_active && !req_is_write;
   assign grspwerr = 1'b0;
   assign grsprerr = 1'b0;
   assign grspid = req_id;
   assign grspuser = req_user;
   assign grsplast = 1'b1;

   always @(*) begin
      grspdata = mem[req_addr[31:2]];
   end
   
   always @(posedge clk) begin
      if (reset) begin
	req_active <= 1'b0;
      end else if (greqvalid && greqready) begin
	 req_active <= 1'b1;
	 req_id <= greqid;
         req_user <= grequser;
	 req_addr <= greqaddr;
	 req_is_write <= greqwrite;
	 req_data <= greqdata;
         req_size <= greqsize;
      end else if (grspready || req_is_write) begin
	 req_active <= 1'b0;
      end
   end // always @ (posedge clk)
   
   always @(posedge clk) begin
      if (req_active && req_is_write) begin
         if (req_size == 2'b00)
	   mem[req_addr[31:2]][(8*req_addr[1:0])+:8] <= req_data[(8*req_addr[1:0])+:8];
         else if (req_size == 2'b01)
	   mem[req_addr[31:2]][(16*req_addr[1])+:16] <= req_data[(16*req_addr[1])+:16];
         else
	   mem[req_addr[31:2]] <= req_data;
      end
   end

endmodule // GBXSRAM

   
