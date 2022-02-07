package it.pagopa.pdnd.interop.uservice.authorizationprocess.service.impl

import com.nimbusds.jose.crypto.{ECDSASigner, Ed25519Signer, RSASSASigner}
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.{JOSEObjectType, JWSAlgorithm, JWSHeader, JWSSigner}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import it.pagopa.pdnd.interop.commons.vault.service.VaultService
import it.pagopa.pdnd.interop.uservice.authorizationprocess.model.token.TokenSeed
import it.pagopa.pdnd.interop.uservice.authorizationprocess.service.JWTGenerator
import org.slf4j.{Logger, LoggerFactory}

import java.util.Date
import scala.concurrent.Future
import scala.jdk.CollectionConverters.SeqHasAsJava
import scala.util.{Random, Try}

final case class JWTGeneratorImpl(vaultService: VaultService) extends JWTGenerator {
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

//   TODO: Start
//   TODO: this part is static and initialized at the start up
//   TODO - use a def instead of a val, but this approach generate to many calls to the vault
//   TODO - use a refreshing cache, more complex

  val rsaPrivateKey: Try[Map[String, String]] = Try {
    val path = VaultSecretPaths.extractPrivateKeysPath("rsa")
    vaultService.readBase64EncodedData(path)
  }

  val ecPrivateKey: Try[Map[String, String]] = Try {
    val path = VaultSecretPaths.extractPrivateKeysPath("ec")
    vaultService.readBase64EncodedData(path)
  }
  //  TODO:End

  private val purposesClaimName: String = "purposes"

  override def generate(jwt: SignedJWT, audience: List[String], purposes: String): Future[String] = Future.fromTry {
    for {
      key    <- getPrivateKey(jwt)
      seed   <- TokenSeed.create(jwt, key, audience, purposes)
      token  <- createToken(seed)
      signer <- getSigner(seed.algorithm, key)
      signed <- signToken(token, signer)
      _ = logger.info("Token generated")
    } yield toBase64(signed)
  }

  def getPrivateKey(jwt: SignedJWT): Try[JWK] = {
    val keys: Try[Map[String, String]] = jwt.getHeader.getAlgorithm match {
      case JWSAlgorithm.RS256 | JWSAlgorithm.RS384 | JWSAlgorithm.RS512                       => rsaPrivateKey
      case JWSAlgorithm.PS256 | JWSAlgorithm.PS384 | JWSAlgorithm.PS256                       => rsaPrivateKey
      case JWSAlgorithm.ES256 | JWSAlgorithm.ES384 | JWSAlgorithm.ES512 | JWSAlgorithm.ES256K => ecPrivateKey
      case JWSAlgorithm.EdDSA                                                                 => ecPrivateKey

    }

    val randomKey: Try[(String, String)] = keys.flatMap(ks =>
      Random
        .shuffle(ks)
        .take(1)
        .headOption
        .toRight(new RuntimeException("PDND private key not found"))
        .toTry
    )

    randomKey.flatMap { case (k, v) =>
      logger.info(s"Using key $k to sign JWT for subject ${jwt.getJWTClaimsSet.getSubject}")
      readPrivateKeyFromString(v)
    }

  }

  private def createToken(seed: TokenSeed): Try[SignedJWT] = Try {
    val issuedAt: Date       = new Date(seed.issuedAt)
    val notBeforeTime: Date  = new Date(seed.nbf)
    val expirationTime: Date = new Date(seed.expireAt)

    val header: JWSHeader = new JWSHeader.Builder(seed.algorithm)
      .customParam("use", "sig")
      .`type`(JOSEObjectType.JWT)
      .keyID(seed.kid)
      .build()

    val payload: JWTClaimsSet = new JWTClaimsSet.Builder()
      .issuer(seed.issuer)
      .audience(seed.audience.asJava)
      .subject(seed.clientId)
      .issueTime(issuedAt)
      .notBeforeTime(notBeforeTime)
      .expirationTime(expirationTime)
      .claim(purposesClaimName, seed.purposes)
      .build()

    new SignedJWT(header, payload)

  }

  def getSigner(algorithm: JWSAlgorithm, key: JWK): Try[JWSSigner] = {
    algorithm match {
      case JWSAlgorithm.RS256 | JWSAlgorithm.RS384 | JWSAlgorithm.RS512                       => rsa(key)
      case JWSAlgorithm.PS256 | JWSAlgorithm.PS384 | JWSAlgorithm.PS256                       => rsa(key)
      case JWSAlgorithm.ES256 | JWSAlgorithm.ES384 | JWSAlgorithm.ES512 | JWSAlgorithm.ES256K => ec(key)
      case JWSAlgorithm.EdDSA                                                                 => octect(key)

    }
  }

  private def readPrivateKeyFromString(keyString: String): Try[JWK] = Try {
    JWK.parse(keyString)
  }

  private def rsa(jwk: JWK): Try[JWSSigner] = Try(new RSASSASigner(jwk.toRSAKey))

  private def ec(jwk: JWK): Try[JWSSigner] = Try(new ECDSASigner(jwk.toECKey))

  private def octect(jwk: JWK): Try[JWSSigner] = Try(new Ed25519Signer(jwk.toOctetKeyPair))

  private def signToken(jwt: SignedJWT, signer: JWSSigner): Try[SignedJWT] = Try {
    val _ = jwt.sign(signer)
    jwt
  }

  private def toBase64(jwt: SignedJWT): String = {
    s"""${jwt.getHeader.toBase64URL}.${jwt.getPayload.toBase64URL}.${jwt.getSignature}"""
  }

}
