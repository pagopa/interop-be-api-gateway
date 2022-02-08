package it.pagopa.interop.api.gateway.model

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jwt.SignedJWT
import it.pagopa.interop.api.gateway.common.ApplicationConfiguration

import java.time.{Clock, Instant, ZoneId}
import java.util.UUID
import scala.util.Try

final case class TokenSeed(
  id: UUID,
  algorithm: JWSAlgorithm,
  kid: String,
  clientId: String,
  issuer: String,
  issuedAt: Long,
  nbf: Long,
  expireAt: Long,
  audience: List[String],
  purposes: String
)

//TODO! Replace with TokenSeed of commons
object TokenSeed {

  val expireIn: Long = 0L

  def create(assertion: SignedJWT, key: JWK, audience: List[String], purposes: String): Try[TokenSeed] = Try {
    val issuedAt = Instant.now(Clock.system(ZoneId.of("UTC")))
    TokenSeed(
      id = UUID.randomUUID(),
      algorithm = assertion.getHeader.getAlgorithm,
      kid = key.computeThumbprint().toString,
      clientId = assertion.getJWTClaimsSet.getSubject,
      //TODO issuer: only for test purpose, priv/pub key are deployed to key manager associated to this uuid
      issuer = ApplicationConfiguration.getPdndIdIssuer,
      issuedAt = issuedAt.toEpochMilli,
      nbf = issuedAt.toEpochMilli,
      expireAt = issuedAt.plusMillis(expireIn).toEpochMilli,
      audience = audience,
      purposes = purposes
    )

  }
}
