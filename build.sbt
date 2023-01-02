ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

lazy val root = (project in file("."))
  .settings(
    name := "w10k-scala",
    idePackagePrefix := Some("net.davidwiles.w10k"),
    assembly / assemblyJarName := "server.jar"
  )

libraryDependencies += "io.netty" % "netty-all" % "4.1.86.Final"

ThisBuild / assemblyMergeStrategy := {
  case PathList("META-INF", "io.netty.versions.properties")  => MergeStrategy.last
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}