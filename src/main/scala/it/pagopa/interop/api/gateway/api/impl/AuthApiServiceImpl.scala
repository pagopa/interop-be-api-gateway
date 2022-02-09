package it.pagopa.interop.api.gateway.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Route
import it.pagopa.interop.be.gateway.api.AuthApiService
import it.pagopa.interop.be.gateway.model.{ClientCredentialsResponse, Problem}

class AuthApiServiceImpl extends AuthApiService {

  /** Code: 200, Message: an Access token, DataType: ClientCredentialsResponse
    * Code: 401, Message: Unauthorized, DataType: Problem
    * Code: 400, Message: Bad request, DataType: Problem
    */
  override def createToken(
    clientAssertion: String,
    clientAssertionType: String,
    grantType: String,
    clientId: Option[String]
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerClientCredentialsResponse: ToEntityMarshaller[ClientCredentialsResponse],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = ???
}