package it.pagopa.interop.api.gateway.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.api.gateway.common.ApplicationConfiguration
import it.pagopa.interop.api.gateway.error.GatewayErrors._
import it.pagopa.interop.api.gateway.service.{AuthorizationManagementInvoker, AuthorizationManagementService}
import it.pagopa.interop.be.gateway.api.AuthApiService
import it.pagopa.interop.be.gateway.model.TokenType.Bearer
import it.pagopa.interop.be.gateway.model.{ClientCredentialsResponse, Problem}
import it.pagopa.pdnd.interop.commons.jwt.model.{ClientAssertionChecker, ValidClientAssertionRequest}
import it.pagopa.pdnd.interop.commons.jwt.service.{ClientAssertionValidator, PDNDTokenGenerator}
import it.pagopa.pdnd.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.pdnd.interop.commons.utils.TypeConversions._
import it.pagopa.pdnd.interop.uservice.keymanagement.client.model.{ClientComponentState, ClientStatesChain}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class AuthApiServiceImpl(
  authorizationManagementService: AuthorizationManagementService,
  jwtValidator: ClientAssertionValidator,
  pdndTokenGenerator: PDNDTokenGenerator
)(implicit ec: ExecutionContext)
    extends AuthApiService {

  val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](LoggerFactory.getLogger(this.getClass))

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

    val tokenAndCheckerF: Try[(String, ClientAssertionChecker)] = for {
      m2mToken   <- pdndTokenGenerator.generateInternalRSAToken()
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
      _           <- checker.verify(publicKey).toFuture
      purposeUUID <- checker.purposeId.toFutureUUID
      client      <- authorizationManagementService.getClient(subjectUUID)(m2mToken)
      purpose     <- client.purposes.find(_.purposeId == purposeUUID).toFuture(PurposeNotFound(client.id, purposeUUID))
      _           <- checkPurposeState(purpose.states).ifM(Future.successful(()), Future.failed(InactiveClient(client.id)))
      token <- pdndTokenGenerator
        .generate(
          clientAssertion,
          audience = List(""), //  client.audience,  //TODO ! add audience
          customClaims = Map.empty,
          tokenIssuer = ApplicationConfiguration.pdndIdIssuer,
          validityDuration = 0L // client.voucherLifespan     //TODO ! add lifespan
        )
        .toFuture
    } yield ClientCredentialsResponse(
      access_token = token,
      token_type = Bearer,
      expires_in = 600 //TODO ! fix me with proper client lifespan
    )

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

  private def checkPurposeState(statesChain: ClientStatesChain): Future[Boolean] =
    Future.successful(
      statesChain.purpose.state == ClientComponentState.ACTIVE &&
        statesChain.eservice.state == ClientComponentState.ACTIVE &&
        statesChain.agreement.state == ClientComponentState.ACTIVE
    )

}
