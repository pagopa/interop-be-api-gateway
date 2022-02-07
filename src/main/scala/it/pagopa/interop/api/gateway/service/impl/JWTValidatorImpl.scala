package it.pagopa.interop.api.gateway.service.impl

import com.nimbusds.jose.{JWSAlgorithm, JWSVerifier}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import it.pagopa.interop.api.gateway.service.{AuthorizationManagementService, JWTValidator, VaultSecretPaths}
import it.pagopa.pdnd.interop.commons.utils.TypeConversions.TryOps
import it.pagopa.pdnd.interop.commons.utils.TypeConversions.StringOps
import it.pagopa.pdnd.interop.commons.vault.service.VaultService
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

final case class JWTValidatorImpl(keyManager: AuthorizationManagementService, vaultService: VaultService)(implicit
  ex: ExecutionContext
) extends JWTValidator {
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def validate(
    clientAssertion: String,
    clientAssertionType: String,
    grantType: String,
    clientId: Option[UUID]
  )(m2mToken: String): Future[(String, SignedJWT)] =
    for {
      info <- extractJwtInfo(clientAssertion, clientAssertionType, grantType, clientId)
      (jwt, kid, clientId) = info
      clientUUid <- clientId.toFutureUUID
      publicKey  <- keyManager.getKey(clientUUid, kid)(m2mToken)
      verifier   <- getVerifier(jwt.getHeader.getAlgorithm, publicKey.key)
      _ = logger.info("Verify signature")
      verified <- verify(verifier, jwt)
      _ = logger.info("Signature verified")
    } yield clientId -> verified

  override def validateBearer(bearer: String): Future[JWTClaimsSet] = {
    for {
      jwt       <- Try(SignedJWT.parse(bearer)).toFuture
      algorithm <- Try(jwt.getHeader.getAlgorithm).toFuture
      kid       <- Try(jwt.getHeader.getKeyID).toFuture
      verifier  <- getPublicKey(algorithm, kid)
      _ = logger.info("Verify bearer")
      _ <- verify(verifier, jwt)
      _ = logger.info("Bearer verified")
    } yield jwt.getJWTClaimsSet
  }

  private def getPublicKey(algorithm: JWSAlgorithm, kid: String): Future[JWSVerifier] = algorithm match {
    case JWSAlgorithm.RS256 | JWSAlgorithm.RS384 | JWSAlgorithm.RS512 =>
      val result = for {
        vaultPath <- Try { VaultSecretPaths.extractPublicKeysPath("rsa") }
        keys = vaultService.readBase64EncodedData(vaultPath)
        publicKey <- Try(keys(kid))
      } yield publicKey
      result.toFuture.flatMap(key => rsa(key))

    case JWSAlgorithm.ES256 =>
      val result = for {
        vaultPath <- Try { VaultSecretPaths.extractPublicKeysPath("ec") }
        keys = vaultService.readBase64EncodedData(vaultPath)
        publicKey <- Try(keys(kid))
      } yield publicKey
      result.toFuture.flatMap(key => ec(key))

    case _ => Future.failed(new RuntimeException("Invalid key algorithm"))

  }
}
