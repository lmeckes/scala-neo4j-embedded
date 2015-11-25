name := """scala-neo4j"""

version := "0.1.0"

scalaVersion := "2.11.7"

libraryDependencies += "org.neo4j" % "neo4j-community" % "2.3.1"

libraryDependencies += "org.gephi" % "graphstore" % "0.4.3"

libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test"

fork in run := true