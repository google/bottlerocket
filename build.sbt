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

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

// managed dependencies
val chisel3 = "edu.berkeley.cs" %% "chisel3" % "3.0-SNAPSHOT_2017-06-22"
val scalatest = "org.scalatest" %% "scalatest" % "2.2.5"
val scalacheck = "org.scalacheck" %% "scalacheck" % "1.12.4"

lazy val commonSettings = Seq(
  name := "bottlerocket",
  version := "1.0",
  scalaVersion := "2.11.7",
  resolvers ++= Seq(
    Resolver.sonatypeRepo("snapshots"),
    Resolver.sonatypeRepo("releases")
  ),
  libraryDependencies ++= Seq(chisel3, scalatest, scalacheck)
)

lazy val hardfloat = (project in file("./third_party/rocket-chip/hardfloat"))
  .settings(
  commonSettings,
  name := "hardfloat"
  )


lazy val rocketchip = (project in file("./third_party/rocket-chip"))
  .dependsOn(hardfloat)
  .settings(
  commonSettings,
    name := "rocket-chip",
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
  )

lazy val root = (project in file("."))
  .dependsOn(rocketchip)
  .settings(
  commonSettings,
    name := "bottlerocket"
)
