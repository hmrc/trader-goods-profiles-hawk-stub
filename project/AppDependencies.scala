import play.sbt.PlayImport.*
import sbt.*

object AppDependencies {

  private val bootstrapVersion = "9.7.0"
  private val hmrcMongoVersion = "2.4.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"  % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-30"         % hmrcMongoVersion,
    "com.beachape"            %% "enumeratum-play"            % "1.8.1",
    "org.typelevel"           %% "cats-core"                  % "2.12.0",
    "com.github.erosb"        %  "everit-json-schema"         % "1.14.4"
  )

  val test: Seq[ModuleID] = Seq(
  "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVersion            % Test,
  "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"    % hmrcMongoVersion            % Test
  )

  val it = Seq.empty
}
