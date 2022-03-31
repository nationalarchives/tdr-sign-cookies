import Dependencies._

ThisBuild / scalaVersion     := "2.13.8"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

resolvers ++= Seq[Resolver](
  "TDR Releases" at "s3://tdr-releases-mgmt"
)
lazy val root = (project in file("."))
  .settings(
    name := "tdr-sign-cookies",
    libraryDependencies ++= Seq(
      authUtils,
      awsCloudfront,
      awsLambda,
      awsUtils,
      catsEffect,
      circeCore,
      circeParser,
      pureConfig,
      pureConfigCatsEffect,
      typesafeConfig,
      keycloakMock % Test,
      scalaTest % Test,
      wiremock % Test,
      mockito % Test
    )
  )

assembly / assemblyJarName := "sign-cookies.jar"

assembly / assemblyMergeStrategy  := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case PathList("reference.conf") => MergeStrategy.concat
  case _ => MergeStrategy.first
}
Test / fork  := true
Test / javaOptions += s"-Dconfig.file=${sourceDirectory.value}/test/resources/application.conf"
