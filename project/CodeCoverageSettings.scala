import sbt.Setting
import scoverage.ScoverageKeys.*

object CodeCoverageSettings {

  private val excludedPackages: Seq[String] = Seq(
    "prod.*;testOnlyDoNotUseInAppConf.*;app.*;.*(Routes).*;.*(ReverseRoutes).*;.*javascript.*;.*BuildInfo.*;.*$anon.*;.*Application.*",
    ".*\\$anonfun\\$.*",
    ".*\\$anon\\$.*",
    ".*\\$anon.*",
    ".*\\$.*\\$\\$.*"
  )

  val settings: Seq[Setting[?]] = Seq(
    coverageExcludedFiles := excludedPackages.mkString(";"),
    coverageMinimumStmtTotal := 95,
    coverageFailOnMinimum := true,
    coverageHighlighting := true
  )
}
