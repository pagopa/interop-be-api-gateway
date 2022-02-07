package it.pagopa.interop.api.gateway.service

import com.nimbusds.jwt.SignedJWT

import scala.concurrent.Future

trait JWTGenerator {
  def generate(assertion: SignedJWT, audience: List[String], purposes: String): Future[String]
}
