package uk.gov.nationalarchives.signcookies

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.{TableFor4, Tables}
import uk.gov.nationalarchives.signcookies.Lambda.Config

import java.security.KeyPairGenerator
import java.util.{Base64, UUID}

class ResponseCreatorTest extends AnyFlatSpec with Tables {

  val localhost = "http://localhost:9000"
  val integration = "https://tdr-integration.nationalarchives.gov.uk"
  val staging = "https://tdr-staging.nationalarchives.gov.uk"
  val prod = "https://tdr.nationalarchives.gov.uk"
  val anotherDomain = "https://another-domain.com"

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
      val privateKey = Base64.getEncoder.encodeToString(KeyPairGenerator.getInstance("RSA").genKeyPair().getPrivate.getEncoded)
      "generateResponse" should s"return the origin header $allowedOrigin for $stage and $origin" in {
        ResponseCreator().generateResponse(UUID.randomUUID(), Config(privateKey, "", frontendUrl, "", "", stage, "", ""), origin)
      }
  }
}
