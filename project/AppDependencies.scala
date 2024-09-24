import play.sbt.PlayImport.*
import sbt.*

object AppDependencies {

  private val bootstrapVersion = "9.5.0"
  private val hmrcMongoVersion = "2.2.0"

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"  % bootstrapVersion,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-play-30"         % hmrcMongoVersion,
    "com.beachape"            %% "enumeratum-play"            % "1.8.0",
    "org.typelevel"           %% "cats-core"                  % "2.10.0",
    "com.github.erosb"        %  "everit-json-schema"         % "1.14.4"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVersion            % Test,
    "uk.gov.hmrc.mongo"       %% "hmrc-mongo-test-play-30"    % hmrcMongoVersion            % Test,
  )

  val it = Seq.empty
}
