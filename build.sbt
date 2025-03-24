import com.typesafe.tools.mima.core._

val catsEffectVersion = "3.6.0"
val circeVersion = "0.14.12"
val elasticacheJavaClusterClientVersion = "1.2.0"
val munitCatsEffectVersion = "2.0.0"
val munitScalaCheckVersion = "1.1.0"
val scala213Version = "2.13.16"
val scala3Version = "3.3.5"
val scalaCheckEffectMunitVersion = "2.0.0-M2"
val slf4jVersion = "2.0.17"
val testcontainersVersion = "1.20.6"

inThisBuild(
  Seq(
    crossScalaVersions := Seq(scala213Version, scala3Version),
    developers := List(
      tlGitHubDev("igor-ramazanov", "Igor Ramazanov"),
      tlGitHubDev("janina9395", "Janina Komarova"),
      tlGitHubDev("vlovgr", "Viktor Rudebeck")
    ),
    githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17")),
    licenses := Seq(License.Apache2),
    mimaBinaryIssueFilters += ProblemFilters.exclude[Problem]("spies.internal.*"),
    organization := "com.magine",
    organizationName := "Magine Pro",
    scalaVersion := scala3Version,
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    startYear := Some(2025),
    tlBaseVersion := "4.0",
    tlCiHeaderCheck := true,
    tlCiScalafixCheck := true,
    tlCiScalafmtCheck := true,
    tlFatalWarnings := true,
    tlJdkRelease := Some(8),
    tlUntaggedAreSnapshots := false,
    versionScheme := Some("early-semver")
  )
)

lazy val root = tlCrossRootProject
  .aggregate(core, circe)

lazy val core = crossProject(JVMPlatform)
  .in(file("modules/core"))
  .settings(name := "spies")
  .jvmSettings(
    libraryDependencies ++= Seq(
      "com.amazonaws" % "elasticache-java-cluster-client" % elasticacheJavaClusterClientVersion,
      "org.typelevel" %% "cats-effect-kernel" % catsEffectVersion,
      "org.slf4j" % "slf4j-nop" % slf4jVersion % Test,
      "org.testcontainers" % "testcontainers" % testcontainersVersion % Test,
      "org.typelevel" %% "munit-cats-effect" % munitCatsEffectVersion % Test,
      "org.typelevel" %% "scalacheck-effect-munit" % scalaCheckEffectMunitVersion % Test,
    )
  )

lazy val circe = crossProject(JVMPlatform)
  .in(file("modules/circe"))
  .settings(name := "spies-circe")
  .jvmSettings(
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-parser" % circeVersion,
      "org.scalameta" %% "munit-scalacheck" % munitScalaCheckVersion % Test
    )
  )
  .dependsOn(core)
