import uk.gov.hmrc.DefaultBuildSettings

ThisBuild / majorVersion := 0
ThisBuild / scalaVersion := "3.3.4"

lazy val microservice = Project("trader-goods-profiles-hawk-stub", file("."))
  .disablePlugins(JUnitXmlReportPlugin)
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    // https://www.scala-lang.org/2021/01/12/configuring-and-suppressing-warnings.html
    // suppress warnings in generated routes files
    scalacOptions += "-Wconf:src=routes/.*:s",
    scalacOptions += "-Yretain-trees",
  )
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(CodeCoverageSettings.settings: _*)
  .settings(PlayKeys.playDefaultPort := 10908)

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(DefaultBuildSettings.itSettings())
  .settings(libraryDependencies ++= AppDependencies.it)
