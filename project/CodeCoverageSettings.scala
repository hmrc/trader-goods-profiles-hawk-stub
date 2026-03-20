import sbt.Setting
import scoverage.ScoverageKeys

object CodeCoverageSettings {

  private val excludedFiles: Seq[String] = Seq(
    ".*Routes.*",
    ".*ReverseRoutes.*",
    ".*router.*",
    ".*javascript.*",
    ".*BuildInfo.*",
    ".*/app/.*",
    ".*/prod/.*",
    ".*/testOnlyDoNotUseInAppConf/.*",
    ".*\\$anonfun\\$.*",
    ".*\\$anon\\$.*",
    ".*\\$anon.*",
    ".*\\$.*\\$\\$.*"
  )

  val settings: Seq[Setting[_]] = Seq(
    ScoverageKeys.coverageExcludedFiles := excludedFiles.mkString(";"),
    ScoverageKeys.coverageMinimumStmtTotal := 90,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}
