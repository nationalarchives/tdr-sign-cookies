import sbt._

object Dependencies {
  val circeVersion = "0.14.1"
  val pureConfigVersion = "0.17.1"

  lazy val authUtils = "uk.gov.nationalarchives" %% "tdr-auth-utils" % "0.0.42"
  lazy val awsCloudfront = "com.amazonaws" % "aws-java-sdk-cloudfront" % "1.12.200"
  lazy val awsLambda = "com.amazonaws" % "aws-lambda-java-core" % "1.2.1"
  lazy val awsUtils = "uk.gov.nationalarchives.aws.utils" %% "tdr-aws-utils" % "0.1.19"
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "3.3.11"
  lazy val circeCore = "io.circe" %% "circe-core" % circeVersion
  lazy val circeParser = "io.circe" %% "circe-parser" % circeVersion
  lazy val pureConfig = "com.github.pureconfig" %% "pureconfig" % pureConfigVersion
  lazy val pureConfigCatsEffect = "com.github.pureconfig" %% "pureconfig-cats-effect" % pureConfigVersion
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.11"
  lazy val typesafeConfig = "com.typesafe" % "config" % "1.4.2"
  lazy val wiremock = "com.github.tomakehurst" % "wiremock" % "2.27.2"
  lazy val keycloakMock = "com.tngtech.keycloakmock" % "mock" % "0.11.0"
  lazy val mockito = "org.mockito" %% "mockito-scala" % "1.17.5"
}
