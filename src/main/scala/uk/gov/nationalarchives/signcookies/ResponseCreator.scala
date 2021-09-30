package uk.gov.nationalarchives.signcookies

import cats.effect.IO
import cats.syntax.option._
import com.amazonaws.services.cloudfront.CloudFrontCookieSigner
import com.amazonaws.services.cloudfront.CloudFrontCookieSigner.CookiesForCustomPolicy
import com.amazonaws.services.cloudfront.util.SignerUtils.Protocol
import uk.gov.nationalarchives.signcookies.Lambda.{Config, LambdaResponse, ResponseHeaders, ResponseMultiValueHeaders}

import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.time.temporal.ChronoUnit
import java.util.{Base64, Date, UUID}

class ResponseCreator(timeUtils: TimeUtils) {

  def createResponse(cookies: CookiesForCustomPolicy, origin: String, config: Config): IO[LambdaResponse] = {
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
    IO(response)
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
    val activeFrom = Date.from(timeUtils.now())
    val expiresOn = Date.from(timeUtils.now().plus(30, ChronoUnit.MINUTES))
    val ipRange = s"0.0.0.0/0"

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
}
object ResponseCreator {
  def apply(timeUtils: TimeUtils) = new ResponseCreator(timeUtils)
}
