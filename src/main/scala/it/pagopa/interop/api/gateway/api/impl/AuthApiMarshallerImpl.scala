package it.pagopa.interop.api.gateway.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.interop.apigateway.api.AuthApiMarshaller
import it.pagopa.interop.apigateway.model.{ClientCredentialsResponse, Problem}

object AuthApiMarshallerImpl extends AuthApiMarshaller {

  override implicit def toEntityMarshallerClientCredentialsResponse: ToEntityMarshaller[ClientCredentialsResponse] =
    sprayJsonMarshaller[ClientCredentialsResponse]

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] =
    sprayJsonMarshaller[Problem]
}
