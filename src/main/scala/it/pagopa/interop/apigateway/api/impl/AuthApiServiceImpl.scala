package it.pagopa.interop.apigateway.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.apigateway.api.AuthApiService
import it.pagopa.interop.apigateway.common.ApplicationConfiguration
import it.pagopa.interop.apigateway.error.GatewayErrors._
import it.pagopa.interop.apigateway.model.TokenType.Bearer
import it.pagopa.interop.apigateway.model.{ClientCredentialsResponse, Problem}
import it.pagopa.interop.apigateway.service.{AuthorizationManagementInvoker, AuthorizationManagementService}
import it.pagopa.interop.authorizationmanagement.client.model.{
  Client,
  ClientComponentState,
  ClientKind,
  ClientStatesChain
}
import it.pagopa.pdnd.interop.commons.jwt.model.{ClientAssertionChecker, RSA, ValidClientAssertionRequest}
import it.pagopa.pdnd.interop.commons.jwt.service.{ClientAssertionValidator, PDNDTokenGenerator}
import it.pagopa.pdnd.interop.commons.jwt.{JWTConfiguration, JWTInternalTokenConfig}
import it.pagopa.pdnd.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.pdnd.interop.commons.utils.TypeConversions._
import org.slf4j.LoggerFactory

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

final case class AuthApiServiceImpl(
  authorizationManagementService: AuthorizationManagementService,
  jwtValidator: ClientAssertionValidator,
  pdndTokenGenerator: PDNDTokenGenerator
)(implicit ec: ExecutionContext)
    extends AuthApiService {

  val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](LoggerFactory.getLogger(this.getClass))

  lazy val jwtConfig: JWTInternalTokenConfig = JWTConfiguration.jwtInternalTokenConfig

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
    val tokenAndCheckerF: Try[(String, ClientAssertionChecker)] = for {
      m2mToken <- pdndTokenGenerator.generateInternalToken(
        jwtAlgorithmType = RSA,
        subject = jwtConfig.subject,
        audience = jwtConfig.audience.toList,
        tokenIssuer = jwtConfig.issuer,
        secondsDuration = jwtConfig.durationInSeconds
      )
      clientUUID <- clientId.traverse(_.toUUID)
      clientAssertionRequest <- ValidClientAssertionRequest.from(
        clientAssertion,
        clientAssertionType,
        grantType,
        clientUUID
      )
      checker <- jwtValidator.extractJwtInfo(clientAssertionRequest)
    } yield (m2mToken, checker)

    val result: Future[ClientCredentialsResponse] = for {
      (m2mToken, checker) <- tokenAndCheckerF.toFuture
      subjectUUID         <- checker.subject.toFutureUUID
      publicKey <- authorizationManagementService
        .getKey(subjectUUID, checker.kid)(m2mToken)
        .map(k => AuthorizationManagementInvoker.serializeKey(k.key))
      _                         <- checker.verify(publicKey).toFuture
      purposeUUID               <- checker.purposeId.toFutureUUID
      client                    <- authorizationManagementService.getClient(subjectUUID)(m2mToken)
      (audience, tokenDuration) <- checkClientValidity(client, purposeUUID)
      token <- pdndTokenGenerator
        .generate(
          clientAssertion,
          audience = audience.toList,
          customClaims = Map.empty,
          tokenIssuer = ApplicationConfiguration.pdndIdIssuer,
          validityDurationInSeconds = tokenDuration.toLong
        )
        .toFuture
    } yield ClientCredentialsResponse(access_token = token, token_type = Bearer, expires_in = tokenDuration)

    onComplete(result) {
      case Success(token) => createToken200(token)
      case Failure(ex: PurposeNotFound) =>
        logger.error("Purpose not found for this client - {}", ex.getMessage)
        createToken400(problemOf(StatusCodes.BadRequest, ex))
      case Failure(ex: InactiveClient) =>
        logger.error("The client performing this request is not active - {}", ex.getMessage)
        createToken400(problemOf(StatusCodes.BadRequest, ex))
      case Failure(_) =>
        complete(StatusCodes.InternalServerError, problemOf(StatusCodes.InternalServerError, CreateTokenRequestError))
    }
  }

  private def checkClientValidity(client: Client, purposeUUID: UUID): Future[(Seq[String], Int)] = {
    def checkPurposeState(statesChain: ClientStatesChain): Future[Boolean] =
      Future.successful(
        statesChain.purpose.state == ClientComponentState.ACTIVE &&
          statesChain.eservice.state == ClientComponentState.ACTIVE &&
          statesChain.agreement.state == ClientComponentState.ACTIVE
      )

    client.kind match {
      case ClientKind.CONSUMER =>
        for {
          purpose <- client.purposes.find(_.purposeId == purposeUUID).toFuture(PurposeNotFound(client.id, purposeUUID))
          eservice = purpose.states.eservice
          checkState <- checkPurposeState(purpose.states)
            .ifM(
              Future.successful((eservice.audience, eservice.voucherLifespan)),
              Future.failed(InactiveClient(client.id))
            )
        } yield checkState
      case ClientKind.API =>
        Future.successful((ApplicationConfiguration.pdndAudience.toSeq, ApplicationConfiguration.pdndTokenDuration))
    }
  }

}
