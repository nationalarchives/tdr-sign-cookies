package uk.gov.nationalarchives.signcookies

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.typesafe.scalalogging.Logger
import io.circe.Printer._
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax._
import uk.gov.nationalarchives.aws.utils.Clients.kms
import uk.gov.nationalarchives.aws.utils.KMSUtils
import uk.gov.nationalarchives.signcookies.Lambda._
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment, Token}

import java.io.{InputStream, OutputStream}
import java.nio.charset.Charset
import scala.concurrent.ExecutionContextExecutor
import scala.io.Source

class Lambda extends RequestStreamHandler {
  val logger: Logger = Logger(this.getClass)
  implicit val executionContext: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  private def decryptVariables(config: Config): Config = {
    val encryptionContext = Map("LambdaFunctionName" -> config.functionName)
    val kmsUtils = KMSUtils(kms(config.kmsEndpoint), encryptionContext)
    config.copy(
      privateKey = kmsUtils.decryptValue(config.privateKey),
      authUrl = kmsUtils.decryptValue(config.authUrl),
      frontendUrl = kmsUtils.decryptValue(config.frontendUrl),
      environment = kmsUtils.decryptValue(config.environment),
      uploadDomain = kmsUtils.decryptValue(config.uploadDomain),
      keyPairId = kmsUtils.decryptValue(config.keyPairId)
    )
  }

  private def validateToken(authUrl: String, token: String): IO[Token] = {
    implicit val deployment: TdrKeycloakDeployment = TdrKeycloakDeployment(authUrl, "tdr", 60)
    val e = KeycloakUtils().token(token)
    IO.fromEither(e)
  }

  def write(outputStream: OutputStream, output: String): IO[Unit] = {
    Resource.make {
      IO(outputStream)
    } { outStream =>
      IO(outStream.close()).handleErrorWith(_ => IO.unit)
    }.use {
      o => IO(o.write(output.getBytes(Charset.forName("UTF-8"))))
    }
  }

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    val rawInput = Source.fromInputStream(input).mkString
    val responseCreator = ResponseCreator(new TimeUtilsImpl())
    val response = for {
      lambdaInput <- IO.fromEither(decode[LambdaInput](rawInput))
      config <- ConfigSource.default.loadF[IO, Config].map(decryptVariables)
      validatedToken <- validateToken(config.authUrl, lambdaInput.headers.Authorization.stripPrefix("Bearer "))
      cookies <- responseCreator.createCookies(validatedToken.userId, config, lambdaInput.requestContext.identity.sourceIp)
      response <- responseCreator.createResponse(cookies, lambdaInput.headers.origin, config)
      _ <- write(output, response.asJson.printWith(noSpaces))
    } yield response

    response.handleErrorWith(err => {
      logger.error("Error getting the signed cookies", err)
      val lambdaResponse = LambdaResponse(401).asJson.printWith(noSpaces)
      write(output, lambdaResponse)
    }).unsafeRunSync()
  }
}

object Lambda {
  case class Headers(Authorization: String, origin: String)

  case class LambdaIdentity(sourceIp: String)

  case class LambdaRequestContext(identity: LambdaIdentity)

  case class LambdaInput(headers: Headers, requestContext: LambdaRequestContext)

  case class ResponseHeaders(`Access-Control-Allow-Origin`: String, `Access-Control-Allow-Credentials`: String)

  case class ResponseMultiValueHeaders(`Set-Cookie`: List[String])

  case class LambdaResponse(statusCode: Int, headers: Option[ResponseHeaders] = None, multiValueHeaders: Option[ResponseMultiValueHeaders] = None, isBase64Encoded: Boolean = false)

  case class Config(privateKey: String, authUrl: String, frontendUrl: String, functionName: String, kmsEndpoint: String, environment: String, uploadDomain: String, keyPairId: String)
}
