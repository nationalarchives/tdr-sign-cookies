package uk.gov.nationalarchives.signcookies

import cats.effect.{IO, Resource}
import cats.syntax.option._
import cats.syntax._
import cats.effect.unsafe.implicits.global
import com.amazonaws.services.cloudfront.CloudFrontCookieSigner
import com.amazonaws.services.cloudfront.CloudFrontCookieSigner.CookiesForCustomPolicy
import com.amazonaws.services.cloudfront.util.SignerUtils.Protocol
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.typesafe.scalalogging.Logger
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax._
import uk.gov.nationalarchives.aws.utils.Clients.kms
import uk.gov.nationalarchives.aws.utils.KMSUtils
import uk.gov.nationalarchives.signcookies.Lambda._
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment, Token}

import java.io.{InputStream, OutputStream}
import java.nio.charset.Charset
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.time.temporal.ChronoUnit
import java.util.{Base64, Date, UUID}
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

  private def createResponse(cookies: CookiesForCustomPolicy, origin: String, config: Config): IO[String] = {
    val originResponseHeaderValue = if (config.environment == "intg" && origin == "http://localhost:9000") {
      origin
    } else {
      config.frontendUrl
    }

    val suffix = "Path=/; Secure; HttpOnly; SameSite=None"
    val cookieResponse = List(
      s"${cookies.getPolicy.getKey}=${cookies.getPolicy.getValue}; $suffix",
      s"${cookies.getKeyPairId.getKey}=${cookies.getKeyPairId.getValue}; $suffix",
      s"${cookies.getSignature.getKey}=${cookies.getSignature.getValue}; $suffix"
    )
    val headers = ResponseHeaders(originResponseHeaderValue, "true")
    val multiValueHeaders = ResponseMultiValueHeaders(cookieResponse)
    val response = LambdaResponse(200, headers.some, multiValueHeaders.some)
    IO(response.asJson.printWith(Printer.noSpaces))
  }

  def createCookies(userId: UUID, config: Config): IO[CookiesForCustomPolicy] = {
    val decodedCert = Base64.getDecoder.decode(config.privateKey)
    val keySpec = new PKCS8EncodedKeySpec(decodedCert)
    val keyFactory = KeyFactory.getInstance("RSA")
    val privateKey = keyFactory.generatePrivate(keySpec)
    // Authorisation rule: only allow users to upload files to a folder corresponding to their user ID
    val s3ObjectKey = s"$userId/*"
    val protocol = Protocol.https
    val distributionDomain = config.uploadDomain
    val keyPairId = config.keyPairId
    val activeFrom = Date.from(new Date().toInstant.minus(3, ChronoUnit.HOURS))
    val expiresOn = Date.from(new Date().toInstant.plus(3, ChronoUnit.HOURS))
    val ipRange = "0.0.0.0/0"

    IO {
      CloudFrontCookieSigner.getCookiesForCustomPolicy(
        protocol,
        distributionDomain,
        privateKey,
        s3ObjectKey,
        keyPairId,
        expiresOn,
        activeFrom,
        ipRange
      )
    }
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
    val response = for {
      lambdaInput <- IO.fromEither(decode[LambdaInput](rawInput))
      config <- ConfigSource.default.loadF[IO, Config].map(decryptVariables)
      validatedToken <- validateToken(config.authUrl, lambdaInput.headers.Authorization.stripPrefix("Bearer "))
      cookies <- createCookies(validatedToken.userId, config)
      response <- createResponse(cookies, lambdaInput.headers.origin, config)
      _ <- write(output, response)
    } yield response

    response.handleErrorWith(err => {
      logger.error("Error getting the signed cookies", err)
      val lambdaResponse = LambdaResponse(401).asJson.printWith(Printer.noSpaces)
      write(output, lambdaResponse)
    }).unsafeRunSync()

  }
}

object Lambda {
  case class Headers(Authorization: String, origin: String)

  case class LambdaInput(headers: Headers)

  case class ResponseHeaders(`Access-Control-Allow-Origin`: String, `Access-Control-Allow-Credentials`: String)

  case class ResponseMultiValueHeaders(`Set-Cookie`: List[String])

  case class LambdaResponse(statusCode: Int, headers: Option[ResponseHeaders] = None, multiValueHeaders: Option[ResponseMultiValueHeaders] = None, isBase64Encoded: Boolean = false)

  case class Config(privateKey: String, authUrl: String, frontendUrl: String, functionName: String, kmsEndpoint: String, environment: String, uploadDomain: String, keyPairId: String)
}
