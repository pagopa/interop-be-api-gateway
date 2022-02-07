package it.pagopa.pdnd.interop.uservice.authorizationprocess.service

import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}

import java.util.UUID
import scala.concurrent.Future

trait JWTValidator {
  def validate(clientAssertion: String, clientAssertionType: String, grantType: String, clientId: Option[UUID])(
    m2mToken: String
  ): Future[(String, SignedJWT)]

  def validateBearer(bearer: String): Future[JWTClaimsSet]
}
