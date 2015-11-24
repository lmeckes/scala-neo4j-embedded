lazy val commonSettings = Seq(
  organization := "com.lms",
  version := "0.1.0",
  scalaVersion := "2.11.7"
)

lazy val root = (project in file(".")).
  settings(
    name := "scala-neo4j",
    libraryDependencies += "org.neo4j" % "neo4j-community" % "2.3.1",
    libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.4"
  )