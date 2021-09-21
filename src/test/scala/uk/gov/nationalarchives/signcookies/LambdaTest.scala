package uk.gov.nationalarchives.signcookies

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.{post, urlEqualTo}
import com.github.tomakehurst.wiremock.common.FileSource
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.{Parameters, ResponseDefinitionTransformer}
import com.github.tomakehurst.wiremock.http.{Request, ResponseDefinition}
import com.tngtech.keycloakmock.api.KeycloakVerificationMock
import com.tngtech.keycloakmock.api.TokenConfig.aTokenConfig
import io.circe.generic.auto._
import io.circe.parser.decode
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.doNothing
import org.mockito.MockitoSugar.mock
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import uk.gov.nationalarchives.signcookies.Lambda.LambdaResponse

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.security.KeyPairGenerator
import java.time.Instant
import java.util.{Base64, UUID}

class LambdaTest extends AnyFlatSpec {

  val keycloakMock: KeycloakVerificationMock = new KeycloakVerificationMock(9004, "tdr")
  keycloakMock.start()

  val wiremockKmsEndpoint = new WireMockServer(new WireMockConfiguration().port(9003).extensions(new ResponseDefinitionTransformer {
    override def transform(request: Request, responseDefinition: ResponseDefinition, files: FileSource, parameters: Parameters): ResponseDefinition = {
      case class KMSRequest(CiphertextBlob: String)
      val decoded = decode[KMSRequest](request.getBodyAsString)
      IO.fromEither(decoded).map(req => {
        val decoded = Base64.getDecoder.decode(req.CiphertextBlob).map(_.toChar).mkString
        val cipherBlob = if(decoded == "privateKey") {
          Base64.getEncoder.encodeToString(
            Base64.getEncoder.encode(KeyPairGenerator.getInstance("RSA").genKeyPair().getPrivate.getEncoded)
          )
        } else {
          req.CiphertextBlob
        }
        val charset = Charset.defaultCharset()
        val plainText = charset.newDecoder.decode(ByteBuffer.wrap(cipherBlob.getBytes(charset))).toString
        ResponseDefinitionBuilder
          .like(responseDefinition)
          .withBody(s"""{"Plaintext": "${plainText}"}""")
          .build()
      }).unsafeRunSync()
    }
    override def getName: String = ""
  }))

  "the lambda" should "return 401 if the token is expired" in {
    wiremockKmsEndpoint.start()
    wiremockKmsEndpoint.stubFor(post(urlEqualTo("/")))
    val userId = UUID.randomUUID()
    val accessToken = keycloakMock.getAccessToken(
      aTokenConfig()
      .withResourceRole("tdr", "tdr_user")
      .withClaim("user_id", userId)
        .withExpiration(Instant.now().minusSeconds(3600))
      .build
    )
    val input =
      s"""{
         |  "headers": {
         |    "Authorization": "Bearer $accessToken",
         |    "origin": "http://localhost:9000"
         |  }
         |}""".stripMargin
    val inputStream = new ByteArrayInputStream(input.getBytes)

    val outputStream = mock[ByteArrayOutputStream]
    val byteArrayCaptor: ArgumentCaptor[Array[Byte]] = ArgumentCaptor.forClass(classOf[Array[Byte]])
    doNothing().when(outputStream).write(byteArrayCaptor.capture())
    new Lambda().handleRequest(inputStream, outputStream, null)
    decode[LambdaResponse](byteArrayCaptor.getValue.map(_.toChar).mkString).toOption.get.statusCode should equal(401)
    wiremockKmsEndpoint.stop()
  }

  "the lambda" should "return 401 if the token is not a valid jwt token" in {
    wiremockKmsEndpoint.start()
    wiremockKmsEndpoint.stubFor(post(urlEqualTo("/")))
    val userId = UUID.randomUUID()
    val accessToken = "faketoken"
    val input =
      s"""{
         |  "headers": {
         |    "Authorization": "Bearer $accessToken",
         |    "origin": "http://localhost:9000"
         |  }
         |}""".stripMargin
    val inputStream = new ByteArrayInputStream(input.getBytes)

    val outputStream = mock[ByteArrayOutputStream]
    val byteArrayCaptor: ArgumentCaptor[Array[Byte]] = ArgumentCaptor.forClass(classOf[Array[Byte]])
    doNothing().when(outputStream).write(byteArrayCaptor.capture())
    new Lambda().handleRequest(inputStream, outputStream, null)
    decode[LambdaResponse](byteArrayCaptor.getValue.map(_.toChar).mkString).toOption.get.statusCode should equal(401)
    wiremockKmsEndpoint.stop()
  }

  "the lambda" should "return 401 if the token has no user id" in {
    wiremockKmsEndpoint.start()
    wiremockKmsEndpoint.stubFor(post(urlEqualTo("/")))
    val userId = UUID.randomUUID()
    val accessToken = keycloakMock.getAccessToken(
      aTokenConfig()
        .withResourceRole("tdr", "tdr_user")
        .withExpiration(Instant.now().minusSeconds(3600))
        .build
    )
    val input =
      s"""{
         |  "headers": {
         |    "Authorization": "Bearer $accessToken",
         |    "origin": "http://localhost:9000"
         |  }
         |}""".stripMargin
    val inputStream = new ByteArrayInputStream(input.getBytes)

    val outputStream = mock[ByteArrayOutputStream]
    val byteArrayCaptor: ArgumentCaptor[Array[Byte]] = ArgumentCaptor.forClass(classOf[Array[Byte]])
    doNothing().when(outputStream).write(byteArrayCaptor.capture())
    new Lambda().handleRequest(inputStream, outputStream, null)
    decode[LambdaResponse](byteArrayCaptor.getValue.map(_.toChar).mkString).toOption.get.statusCode should equal(401)
    wiremockKmsEndpoint.stop()

  }

  "the lambda" should "return 200 if the token is valid" in {
    wiremockKmsEndpoint.start()
    wiremockKmsEndpoint.stubFor(post(urlEqualTo("/")))
    val userId = UUID.randomUUID()
    val accessToken = keycloakMock.getAccessToken(
      aTokenConfig()
        .withResourceRole("tdr", "tdr_user")
        .withClaim("user_id", userId)
        .withExpiration(Instant.now().plusSeconds(3600))
        .build
    )
    val input =
      s"""{
         |  "headers": {
         |    "Authorization": "Bearer $accessToken",
         |    "origin": "http://localhost:9000"
         |  }
         |}""".stripMargin
    val inputStream = new ByteArrayInputStream(input.getBytes)

    val outputStream = mock[ByteArrayOutputStream]
    val byteArrayCaptor: ArgumentCaptor[Array[Byte]] = ArgumentCaptor.forClass(classOf[Array[Byte]])
    doNothing().when(outputStream).write(byteArrayCaptor.capture())
    new Lambda().handleRequest(inputStream, outputStream, null)
    decode[LambdaResponse](byteArrayCaptor.getValue.map(_.toChar).mkString).toOption.get.statusCode should equal(200)
    wiremockKmsEndpoint.stop()
  }

  "the lambda" should "return allowed origin localhost if the origin is localhost" in {
    wiremockKmsEndpoint.start()
    wiremockKmsEndpoint.stubFor(post(urlEqualTo("/")))
    val userId = UUID.randomUUID()
    val accessToken = keycloakMock.getAccessToken(
      aTokenConfig()
        .withResourceRole("tdr", "tdr_user")
        .withClaim("user_id", userId)
        .withExpiration(Instant.now().plusSeconds(3600))
        .build
    )
    val input =
      s"""{
         |  "headers": {
         |    "Authorization": "Bearer $accessToken",
         |    "origin": "http://localhost:9000"
         |  }
         |}""".stripMargin
    val inputStream = new ByteArrayInputStream(input.getBytes)

    val outputStream = mock[ByteArrayOutputStream]
    val byteArrayCaptor: ArgumentCaptor[Array[Byte]] = ArgumentCaptor.forClass(classOf[Array[Byte]])
    doNothing().when(outputStream).write(byteArrayCaptor.capture())
    new Lambda().handleRequest(inputStream, outputStream, null)
    decode[LambdaResponse](byteArrayCaptor.getValue.map(_.toChar).mkString).toOption.get.headers.get.`Access-Control-Allow-Origin` should equal("http://localhost:9000")
    wiremockKmsEndpoint.stop()
  }

  "the lambda" should "return allowed origin integration if the origin is integration" in {
    wiremockKmsEndpoint.start()
    wiremockKmsEndpoint.stubFor(post(urlEqualTo("/")))
    val userId = UUID.randomUUID()
    val accessToken = keycloakMock.getAccessToken(
      aTokenConfig()
        .withResourceRole("tdr", "tdr_user")
        .withClaim("user_id", userId)
        .withExpiration(Instant.now().plusSeconds(3600))
        .build
    )
    val input =
      s"""{
         |  "headers": {
         |    "Authorization": "Bearer $accessToken",
         |    "origin": "https://tdr-integration.nationalarchives.gov.uk"
         |  }
         |}""".stripMargin
    val inputStream = new ByteArrayInputStream(input.getBytes)

    val outputStream = mock[ByteArrayOutputStream]
    val byteArrayCaptor: ArgumentCaptor[Array[Byte]] = ArgumentCaptor.forClass(classOf[Array[Byte]])
    doNothing().when(outputStream).write(byteArrayCaptor.capture())
    new Lambda().handleRequest(inputStream, outputStream, null)
    decode[LambdaResponse](byteArrayCaptor.getValue.map(_.toChar).mkString).toOption.get.headers.get.`Access-Control-Allow-Origin` should equal("https://tdr-integration.nationalarchives.gov.uk")
    wiremockKmsEndpoint.stop()
  }

  "the lambda" should "return allowed origin integration if the origin is an unknown domain" in {
    wiremockKmsEndpoint.start()
    wiremockKmsEndpoint.stubFor(post(urlEqualTo("/")))
    val userId = UUID.randomUUID()
    val accessToken = keycloakMock.getAccessToken(
      aTokenConfig()
        .withResourceRole("tdr", "tdr_user")
        .withClaim("user_id", userId)
        .withExpiration(Instant.now().plusSeconds(3600))
        .build
    )
    val input =
      s"""{
         |  "headers": {
         |    "Authorization": "Bearer $accessToken",
         |    "origin": "https://anotherdomain.com"
         |  }
         |}""".stripMargin
    val inputStream = new ByteArrayInputStream(input.getBytes)

    val outputStream = mock[ByteArrayOutputStream]
    val byteArrayCaptor: ArgumentCaptor[Array[Byte]] = ArgumentCaptor.forClass(classOf[Array[Byte]])
    doNothing().when(outputStream).write(byteArrayCaptor.capture())
    new Lambda().handleRequest(inputStream, outputStream, null)
    decode[LambdaResponse](byteArrayCaptor.getValue.map(_.toChar).mkString).toOption.get.headers.get.`Access-Control-Allow-Origin` should equal("https://tdr-integration.nationalarchives.gov.uk")
    wiremockKmsEndpoint.stop()
  }
}
