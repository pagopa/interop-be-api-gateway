package it.pagopa.interop.apigateway.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
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
import it.pagopa.interop.commons.jwt.model.{ClientAssertionChecker, RSA, ValidClientAssertionRequest}
import it.pagopa.interop.commons.jwt.service.{ClientAssertionValidator, InteropTokenGenerator}
import it.pagopa.interop.commons.jwt.{JWTConfiguration, JWTInternalTokenConfig}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.ComponentError
import it.pagopa.interop.commons.utils.{BEARER, CORRELATION_ID_HEADER, PURPOSE_ID_CLAIM, ORGANIZATION_ID_CLAIM}
import org.slf4j.LoggerFactory

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

final case class AuthApiServiceImpl(
  authorizationManagementService: AuthorizationManagementService,
  jwtValidator: ClientAssertionValidator,
  interopTokenGenerator: InteropTokenGenerator
)(implicit ec: ExecutionContext)
    extends AuthApiService {

  val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](LoggerFactory.getLogger(this.getClass))

  lazy val jwtConfig: JWTInternalTokenConfig = JWTConfiguration.jwtInternalTokenConfig

  override def createToken(
    clientId: Option[String],
    clientAssertion: String,
    clientAssertionType: String,
    grantType: String
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerClientCredentialsResponse: ToEntityMarshaller[ClientCredentialsResponse],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val tokenAndChecker: Try[(String, ClientAssertionChecker)] = for {
      m2mToken               <- interopTokenGenerator.generateInternalToken(
        jwtAlgorithmType = RSA,
        subject = jwtConfig.subject,
        audience = jwtConfig.audience.toList,
        tokenIssuer = jwtConfig.issuer,
        secondsDuration = jwtConfig.durationInSeconds
      )
      clientUUID             <- clientId.traverse(_.toUUID)
      clientAssertionRequest <- ValidClientAssertionRequest.from(
        clientAssertion,
        clientAssertionType,
        grantType,
        clientUUID
      )
      checker                <- jwtValidator.extractJwtInfo(clientAssertionRequest)
    } yield (m2mToken, checker)

    val result: Future[ClientCredentialsResponse] = for {
      (m2mToken, checker) <- tokenAndChecker.toFuture
      m2mContexts = Seq(CORRELATION_ID_HEADER -> UUID.randomUUID().toString, BEARER -> m2mToken)
      clientUUID                <- checker.subject.toFutureUUID
      publicKey                 <- authorizationManagementService
        .getKey(clientUUID, checker.kid)(m2mContexts)
        .map(k => AuthorizationManagementInvoker.serializeKey(k.key))
      _                         <- checker.verify(publicKey).toFuture
      purposeId                 <- checker.purposeId.traverse(_.toFutureUUID)
      client                    <- authorizationManagementService.getClient(clientUUID)(m2mContexts)
      (audience, tokenDuration) <- checkClientValidity(client, purposeId)
      customClaims              <- getCustomClaims(client, purposeId)
      token                     <- interopTokenGenerator
        .generate(
          clientAssertion = clientAssertion,
          audience = audience.toList,
          customClaims = customClaims,
          tokenIssuer = ApplicationConfiguration.interopIdIssuer,
          validityDurationInSeconds = tokenDuration.toLong
        )
        .toFuture
    } yield ClientCredentialsResponse(access_token = token, token_type = Bearer, expires_in = tokenDuration)

    onComplete(result) {
      case Success(token)               => createToken200(token)
      case Failure(ex: PurposeNotFound) =>
        logger.error(s"Purpose not found for this client - ${ex.getMessage}")
        createToken400(problemOf(StatusCodes.BadRequest, ex))
      case Failure(ex: InactiveClient)  =>
        logger.error(s"The client performing this request is not active - ${ex.getMessage}")
        createToken400(problemOf(StatusCodes.BadRequest, ex))
      case Failure(ex)                  =>
        logger.error(s"Error while creating a token for this request - ${ex.getMessage}")
        complete(
          StatusCodes.InternalServerError,
          problemOf(StatusCodes.InternalServerError, CreateTokenRequestError(ex.getMessage))
        )
    }
  }

  private def checkClientValidity(client: Client, purposeId: Option[UUID]): Future[(Seq[String], Int)] = {
    def checkClientStates(statesChain: ClientStatesChain): Future[(Seq[String], Int)] = {

      def validate(
        state: ClientComponentState,
        error: ComponentError
      ): Validated[NonEmptyList[ComponentError], ClientComponentState] =
        Validated.validNel(state).ensureOr(_ => NonEmptyList.one(error))(_ == ClientComponentState.ACTIVE)

      val validation
        : Validated[NonEmptyList[ComponentError], (ClientComponentState, ClientComponentState, ClientComponentState)] =
        (
          validate(statesChain.purpose.state, InactivePurpose(statesChain.purpose.state.toString)),
          validate(statesChain.eservice.state, InactiveEservice(statesChain.eservice.state.toString)),
          validate(statesChain.agreement.state, InactiveAgreement(statesChain.agreement.state.toString))
        ).tupled

      validation match {
        case Invalid(e) => Future.failed(InactiveClient(client.id, e.map(_.getMessage).toList))
        case Valid(_)   => Future.successful((statesChain.eservice.audience, statesChain.eservice.voucherLifespan))
      }

    }

    client.kind match {
      case ClientKind.CONSUMER =>
        for {
          purposeUUID <- purposeId.toFuture(PurposeIdNotProvided)
          purpose     <- client.purposes
            .find(_.purposeId == purposeUUID)
            .toFuture(PurposeNotFound(client.id, purposeUUID))
          checkState  <- checkClientStates(purpose.states)
        } yield checkState
      case ClientKind.API      =>
        Future.successful(
          (ApplicationConfiguration.interopAudience.toSeq, ApplicationConfiguration.interopTokenDuration)
        )
    }
  }

  private def getCustomClaims(client: Client, purposeId: Option[UUID]): Future[Map[String, String]] =
    client.kind match {
      case ClientKind.CONSUMER => purposeId.toFuture(PurposeIdNotProvided).map(p => Map(PURPOSE_ID_CLAIM -> p.toString))
      case ClientKind.API      => Future.successful(Map(ORGANIZATION_ID_CLAIM -> client.consumerId.toString))
    }

}
