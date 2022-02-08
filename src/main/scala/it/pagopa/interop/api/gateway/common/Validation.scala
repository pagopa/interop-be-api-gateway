package it.pagopa.interop.api.gateway.common

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import it.pagopa.pdnd.interop.commons.jwt.errors.{
  InvalidAccessTokenRequest,
  InvalidClientAssertionType,
  InvalidGrantType
}

import scala.util.{Failure, Success, Try}

trait Validation {

  //TODO! This may be already present in common. Consider to generalize.

  final val jwtBearerClientAssertionType: String = "urn%3Aietf%3Aparams%3Aoauth%3Aclient-assertion-type%3Ajwt-bearer"
  final val clientCredentialsGrantType: String   = "client_credentials"

  def validateAccessTokenRequest(clientAssertionType: String, grantType: String): Try[Unit] = {
    val result: Validated[NonEmptyList[Throwable], Unit] =
      (validateClientAssertionType(clientAssertionType), validateGrantType(grantType)).mapN((_: Unit, _: Unit) => ())

    result match {
      case Valid(unit) => Success(unit)
      case Invalid(e)  => Failure(InvalidAccessTokenRequest(e.map(_.getMessage).toList))
    }
  }

  private def validateClientAssertionType(clientAssertionType: String): ValidatedNel[Throwable, Unit] = {
    val validation = Either.cond(
      clientAssertionType == jwtBearerClientAssertionType,
      (),
      InvalidClientAssertionType(clientAssertionType)
    )

    validation match {
      case Left(throwable) => throwable.invalidNel[Unit]
      case Right(_)        => ().validNel[Throwable]
    }

  }

  private def validateGrantType(grantType: String): ValidatedNel[Throwable, Unit] = {
    val validation = Either.cond(grantType == clientCredentialsGrantType, (), InvalidGrantType(grantType))

    validation match {
      case Left(throwable) => throwable.invalidNel[Unit]
      case Right(_)        => ().validNel[Throwable]
    }
  }

}
