package it.pagopa.interop.api.gateway.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Route
import it.pagopa.interop.be.gateway.api.AuthApiService
import it.pagopa.interop.be.gateway.model.{ClientCredentialsResponse, Problem}
import it.pagopa.pdnd.interop.commons.jwt.service.{PDNDTokenGenerator, ClientAssertionValidator}

class AuthApiServiceImpl(jwtValidator: ClientAssertionValidator, pdndTokenGenerator: PDNDTokenGenerator)
    extends AuthApiService {

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
  ): Route = {

    for {

      m2mToken <- pdndTokenGenerator.generateInternalRSAToken() // TODO! env var in configuration
      subject  <- extractJwtInfo()
      // client    <- authorizationManagementService.getClient(clientId)(m2mToken)
      // _         <- clientMustBeActive(client)
      validated <- jwtValidator.validate(clientAssertion, clientAssertionType, grantType, clientId)(m2mToken)

      // for {
      //   m2mToken  <- m2mAuthorizationService.token // interop
      //   validated <- jwtValidator.validate(clientAssertion, clientAssertionType, grantType, clientId)(m2mToken)
      //   client    <- authorizationManagementService.getClient(clientUuid)(m2mToken)
      //   _         <- clientMustBeActive(client)
      //   token     <- jwtGenerator.generate(assertion, descriptorAudience, client.purposes)
      // } yield token
    } yield null

    ???
  }
}
