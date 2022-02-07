package it.pagopa.pdnd.interop.uservice.authorizationprocess.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.be.gateway.api.AuthApiMarshaller
import it.pagopa.interop.be.gateway.model.{ClientCredentialsResponse, Problem}
import it.pagopa.pdnd.interop.commons.utils.SprayCommonFormats.uuidFormat

import java.util.UUID

object AuthApiMarshallerImpl extends AuthApiMarshaller {
  override implicit def fromEntityUnmarshallerUUID: FromEntityUnmarshaller[UUID] =
    sprayJsonUnmarshaller[UUID](uuidFormat.read)

  override implicit def toEntityMarshallerClientCredentialsResponse: ToEntityMarshaller[ClientCredentialsResponse] =
    sprayJsonMarshaller[ClientCredentialsResponse]

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] =
    sprayJsonMarshaller[Problem]
}
