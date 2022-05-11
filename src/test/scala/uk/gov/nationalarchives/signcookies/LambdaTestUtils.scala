package uk.gov.nationalarchives.signcookies

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.common.FileSource
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.{Parameters, ResponseDefinitionTransformer}
import com.github.tomakehurst.wiremock.http.{Request, ResponseDefinition}
import com.tngtech.keycloakmock.api.{KeycloakMock, ServerConfig}
import com.tngtech.keycloakmock.api.TokenConfig.aTokenConfig

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.security.KeyPairGenerator
import java.util.{Base64, UUID}
import io.circe.generic.auto._
import io.circe.parser.decode
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.doNothing
import org.mockito.MockitoSugar.mock

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.time.Instant

object LambdaTestUtils {
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
          .withBody(s"""{"Plaintext": "$plainText"}""")
          .build()
      }).unsafeRunSync()
    }
    override def getName: String = ""
  }))
  val config: ServerConfig = ServerConfig.aServerConfig
    .withPort(9004)
    .withDefaultRealm("tdr")
    .build()
  val keycloakMock: KeycloakMock = new KeycloakMock(config)

  def createAccessToken(userId: Option[UUID], expiration: Instant = Instant.now().plusSeconds(3600)): String = {
    val baseConfig = aTokenConfig()
      .withResourceRole("tdr", "tdr_user")
      .withExpiration(expiration)

    val tokenConfig = userId
      .map(userId => baseConfig.withClaim("user_id", userId)).getOrElse(baseConfig)
    keycloakMock.getAccessToken(tokenConfig.build())
  }

  def getInputStream(accessToken: String, origin: String = "http://localhost:9000", originKey: String = "origin"): ByteArrayInputStream = {
    val input = s"""{
       |  "headers": {
       |    "Authorization": "Bearer $accessToken",
       |    "$originKey": "$origin"
       |
       |  },
       |  "requestContext": {
       |    "identity": {
       |      "sourceIp": "0.0.0.0"
       |    }
       |  }
       |}""".stripMargin
    new ByteArrayInputStream(input.getBytes)
  }

  def outputStream(byteArrayCaptor: ArgumentCaptor[Array[Byte]]): ByteArrayOutputStream = {
    val outputStream = mock[ByteArrayOutputStream]
    doNothing().when(outputStream).write(byteArrayCaptor.capture())
    outputStream
  }
}
