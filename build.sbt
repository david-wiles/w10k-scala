ThisBuild / version := "v1"
ThisBuild / scalaVersion := "2.13.10"
ThisBuild / assemblyMergeStrategy := {
  case PathList("META-INF", "io.netty.versions.properties")  => MergeStrategy.last
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}

lazy val broadcast = (project in file("broadcast"))
  .settings(
    name := "w10k-scala",
    assembly / assemblyJarName := "broadcast.jar"
  )

lazy val client2client = (project in file("client2client"))
  .settings(
    name := "w10k-scala",
    assembly / assemblyJarName := "client2client.jar"
  )