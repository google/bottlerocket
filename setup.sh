#!/bin/sh

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
#
# Setup script to parsimoniously clone submodules

git submodule update --init third_party/rocket-chip
cd third_party/rocket-chip
git submodule update --init --recursive hardfloat
git submodule update --init riscv-tools
cd riscv-tools
git submodule update --init --recursive riscv-tests
