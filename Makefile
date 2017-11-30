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

GENDIR := generated-src

RVEC ?= 4100

default: $(GENDIR)/BottleRocketCore.v

.PHONY: $(GENDIR)/BottleRocketCore.v
$(GENDIR)/BottleRocketCore.v:
	rm -rf generated-src/*
	sbt "runMain bottlerocket.BottleRocketGenerator --target-dir generated-src --reset-vec ${RVEC}"

.PHONY: check-paths test clean

check-paths:
	sbt "runMain bottlerocket.BottleRocketPathChecker --target-dir path-check-output --reset-vec ${RVEC}"

test: $(GENDIR)/BottleRocketCore.v
	$(MAKE) -C test clean
	$(MAKE) -C test

clean:
	$(MAKE) -C test clean
	rm -rf generated-src
