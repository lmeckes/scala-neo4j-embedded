lazy val commonSettings = Seq(
  organization := "com.lms",
  version := "0.1.0",
  scalaVersion := "2.11.7",
  scalaVersion in ThisBuild := "2.11.7",
  ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }
)

lazy val root = (project in file(".")).
  settings(
    name := "scala-neo4j",
    libraryDependencies += "org.neo4j" % "neo4j-community" % "2.3.1",
    libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test"
  )