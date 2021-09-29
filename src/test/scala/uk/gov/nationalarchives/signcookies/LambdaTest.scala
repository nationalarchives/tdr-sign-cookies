package uk.gov.nationalarchives.signcookies

import cats.syntax.option._
import com.github.tomakehurst.wiremock.client.WireMock.{post, urlEqualTo}
import io.circe.generic.auto._
import io.circe.parser.decode
import org.mockito.ArgumentCaptor
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import uk.gov.nationalarchives.signcookies.Lambda.LambdaResponse
import uk.gov.nationalarchives.signcookies.LambdaTestUtils._

import java.time.Instant
import java.util.UUID

class LambdaTest extends AnyFlatSpec with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    wiremockKmsEndpoint.start()
    wiremockKmsEndpoint.stubFor(post(urlEqualTo("/")))
    keycloakMock.start()
  }

  override def afterEach(): Unit = {
    wiremockKmsEndpoint.stop()
    keycloakMock.stop()
  }

  "the lambda" should "return 401 if the token is expired" in {
    val accessToken = createAccessToken(UUID.randomUUID().some, Instant.now().minusSeconds(3600))
    val inputStream = getInputStream(accessToken)
    val byteArrayCaptor: ArgumentCaptor[Array[Byte]] = ArgumentCaptor.forClass(classOf[Array[Byte]])

    new Lambda().handleRequest(inputStream, outputStream(byteArrayCaptor), null)

    decode[LambdaResponse](byteArrayCaptor.getValue.map(_.toChar).mkString).toOption.get.statusCode should equal(401)
  }

  "the lambda" should "return 401 if the token is not a valid jwt token" in {
    val accessToken = "faketoken"
    val inputStream = getInputStream(accessToken)
    val byteArrayCaptor: ArgumentCaptor[Array[Byte]] = ArgumentCaptor.forClass(classOf[Array[Byte]])

    new Lambda().handleRequest(inputStream, outputStream(byteArrayCaptor), null)

    decode[LambdaResponse](byteArrayCaptor.getValue.map(_.toChar).mkString).toOption.get.statusCode should equal(401)
  }

  "the lambda" should "return 401 if the token has no user id" in {
    val accessToken = createAccessToken(None)
    val inputStream = getInputStream(accessToken)
    val byteArrayCaptor: ArgumentCaptor[Array[Byte]] = ArgumentCaptor.forClass(classOf[Array[Byte]])

    new Lambda().handleRequest(inputStream, outputStream(byteArrayCaptor), null)

    decode[LambdaResponse](byteArrayCaptor.getValue.map(_.toChar).mkString).toOption.get.statusCode should equal(401)
  }

  "the lambda" should "return 200 if the token is valid" in {
    val accessToken = createAccessToken(UUID.randomUUID().some)
    val inputStream = getInputStream(accessToken)
    val byteArrayCaptor: ArgumentCaptor[Array[Byte]] = ArgumentCaptor.forClass(classOf[Array[Byte]])

    new Lambda().handleRequest(inputStream, outputStream(byteArrayCaptor), null)

    decode[LambdaResponse](byteArrayCaptor.getValue.map(_.toChar).mkString).toOption.get.statusCode should equal(200)
  }
}
