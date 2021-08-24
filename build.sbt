name         := "github-ranks"
version      := "0.1"
scalaVersion := "2.13.6"

val catsEffectVersion      = "3.2.3"
val circeVersion           = "0.14.1"
val http4sVersion          = "1.0.0-M24"
val logbackVersion         = "1.2.5"
val munitCatsEffectVersion = "1.0.5"
val munitVersion           = "0.7.28"
val scalaLoggingVersion    = "3.9.4"

libraryDependencies ++= Seq(
  "ch.qos.logback"              % "logback-classic"     % logbackVersion,
  "io.circe"                   %% "circe-core"          % circeVersion,
  "io.circe"                   %% "circe-generic"       % circeVersion,
  "io.circe"                   %% "circe-parser"        % circeVersion,
  "org.http4s"                 %% "http4s-circe"        % http4sVersion,
  "org.http4s"                 %% "http4s-dsl"          % http4sVersion,
  "org.http4s"                 %% "http4s-ember-client" % http4sVersion,
  "org.http4s"                 %% "http4s-ember-server" % http4sVersion,
  "org.typelevel"              %% "cats-effect"         % catsEffectVersion,
  "com.typesafe.scala-logging" %% "scala-logging"       % scalaLoggingVersion,

  "org.scalameta" %% "munit"               % munitVersion           % Test,
  "org.typelevel" %% "munit-cats-effect-3" % munitCatsEffectVersion % Test

)

testFrameworks += new TestFramework("munit.Framework")

idePackagePrefix.withRank(KeyRanks.Invisible) := Some("dev.akif.githubranks")
