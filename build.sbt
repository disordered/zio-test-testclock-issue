import sbt.Keys.libraryDependencies

val scala3Version = "3.6.3"

lazy val root = project
  .in(file("."))
  .settings(
    name := "zio-test-testclock-issue",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies += "com.mysql" % "mysql-connector-j" % "8.3.0",
    libraryDependencies += "org.tpolecat" %% "doobie-core" % "1.0.0-RC1",
    libraryDependencies += "dev.zio" %% "zio" % "2.1.14",
    libraryDependencies += "dev.zio" %% "zio-interop-cats" % "23.0.0.5",
    libraryDependencies += "dev.zio" %% "zio-test" % "2.1.14" % Test,
    libraryDependencies += "dev.zio" %% "zio-test-sbt" % "2.1.14" % Test,
    libraryDependencies += "com.dimafeng" %% "testcontainers-scala-mysql" % "0.41.0" % Test,
  )
