name := """Farm"""
organization := "nuge"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)
scalaVersion := "3.3.4"

libraryDependencies ++= Seq(
  guice,
  evolutions,
  jdbc,
  "org.playframework.anorm" %% "anorm" % "2.7.0",
  "org.postgresql" % "postgresql" % "42.7.3",
  specs2 % Test,
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test
)
// Adds additional packages into Twirl
//TwirlKeys.templateImports += "nuge.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "nuge.binders._"
