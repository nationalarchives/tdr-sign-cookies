package uk.gov.nationalarchives.signcookies

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.amazonaws.services.cloudfront.CloudFrontCookieSigner.CookiesForCustomPolicy
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.{TableFor4, Tables}
import uk.gov.nationalarchives.signcookies.Lambda.Config

import java.security.KeyPairGenerator
import java.util.{Base64, UUID}
import io.circe.parser.decode
import io.circe.generic.auto._
import org.scalatest.matchers.should.Matchers._

import java.time.temporal.ChronoUnit

class ResponseCreatorTest extends AnyFlatSpec with Tables {

  case class Condition(IpAddress: IpAddress, DateLessThan: AWSEpoch, DateGreaterThan: AWSEpoch)
  case class AWSEpoch(`AWS:EpochTime`: Int)
  case class IpAddress (`AWS:SourceIp`: String)
  case class Policy(Statement: List[Statement])
  case class Statement (Resource: String,Condition: Condition)

  val localhost = "http://localhost:9000"
  val integration = "https://tdr-integration.nationalarchives.gov.uk"
  val staging = "https://tdr-staging.nationalarchives.gov.uk"
  val prod = "https://tdr.nationalarchives.gov.uk"
  val anotherDomain = "https://another-domain.com"

  val privateKey: String = Base64.getEncoder
    .encodeToString(KeyPairGenerator.getInstance("RSA").genKeyPair().getPrivate.getEncoded)

  def decodeValue(value: String): String = {
    val replacedValue = value.replace('-', '+')
      .replace('_', '=')
      .replace('~', '/')
    Base64.getDecoder.decode(replacedValue).map(_.toChar).mkString
  }

  val origins: TableFor4[String, String, String, String] = Table(
    ("stage", "origin", "allowedOrigin", "frontendUrl"),
    ("intg", localhost, localhost, integration),
    ("intg", integration, integration, integration),
    ("intg", anotherDomain, integration, integration),
    ("staging", staging, staging, staging),
    ("staging", anotherDomain, staging, staging),
    ("prod", prod, prod, prod),
    ("prod", anotherDomain, prod, prod),
  )

  forAll(origins) {
    (stage, origin, allowedOrigin, frontendUrl) =>
      val cookiesForCustomPolicy = new CookiesForCustomPolicy()
      cookiesForCustomPolicy.setPolicy("")
      cookiesForCustomPolicy.setSignature("")
      cookiesForCustomPolicy.setKeyPairId("")
      "generateResponse" should s"return the origin header $allowedOrigin for $stage and $origin" in {
        val response = ResponseCreator(new TestTimeUtils())
          .createResponse(cookiesForCustomPolicy ,origin, Config(privateKey, "", frontendUrl, "", "", stage, "", ""))
          .unsafeRunSync()
        response.headers.get.`Access-Control-Allow-Origin` should equal(allowedOrigin)
      }
  }

  "createCookies" should "set the correct policy" in {
    val userId = UUID.randomUUID()
    val keyPair = "keyPairId"
    val uploadDomain = "upload.domain"
    val testTimeUtils = new TestTimeUtils()
    val config = Config(privateKey, "", "", "", "", "", uploadDomain, keyPair)

    val cookies = ResponseCreator(testTimeUtils)
      .createCookies(userId, config).unsafeRunSync()
    val decodedPolicy = decodeValue(cookies.getPolicy.getValue)
    val statement = IO.fromEither(decode[Policy](decodedPolicy)).unsafeRunSync().Statement.head

    cookies.getKeyPairId.getValue should equal(keyPair)
    statement.Condition.IpAddress.`AWS:SourceIp` should equal(s"0.0.0.0/0")
    statement.Resource should equal(s"https://$uploadDomain/$userId/*")
    statement.Condition.DateLessThan.`AWS:EpochTime` should equal(testTimeUtils.now().plus(30, ChronoUnit.MINUTES).getEpochSecond)
    statement.Condition.DateGreaterThan.`AWS:EpochTime` should equal(testTimeUtils.now().getEpochSecond)
  }
}
