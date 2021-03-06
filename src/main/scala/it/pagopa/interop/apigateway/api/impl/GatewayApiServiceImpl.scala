package it.pagopa.interop.apigateway.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.{Route, StandardRoute}
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementmanagement.client.model.{AgreementState => AgreementManagementApiAgreementState}
import it.pagopa.interop.apigateway.api.GatewayApiService
import it.pagopa.interop.apigateway.error.GatewayErrors._
import it.pagopa.interop.apigateway.model._
import it.pagopa.interop.apigateway.service._
import it.pagopa.interop.authorizationmanagement.client.model.{Client => AuthorizationManagementApiClient}
import it.pagopa.interop.commons.jwt.{M2M_ROLE, authorizeInterop, hasPermissions}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils._
import it.pagopa.interop.commons.utils.ORGANIZATION_ID_CLAIM
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.OperationForbidden
import it.pagopa.interop.purposemanagement.client.model.{Purpose => PurposeManagementApiPurpose}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final case class GatewayApiServiceImpl(
  partyManagementService: PartyManagementService,
  agreementManagementService: AgreementManagementService,
  authorizationManagementService: AuthorizationManagementService,
  catalogManagementService: CatalogManagementService,
  attributeRegistryManagementService: AttributeRegistryManagementService,
  purposeManagementService: PurposeManagementService,
  notifierService: NotifierService
)(implicit ec: ExecutionContext)
    extends GatewayApiService {

  def internalServerError(message: String)(implicit c: ContextFieldsToLog): StandardRoute = {
    logger.error(message)
    complete(StatusCodes.InternalServerError, problemOf(StatusCodes.InternalServerError, InternalServerError))
  }

  private val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private[this] def authorize(
    route: => Route
  )(implicit contexts: Seq[(String, String)], toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
    authorizeInterop(hasPermissions(M2M_ROLE), problemOf(StatusCodes.Forbidden, OperationForbidden)) {
      route
    }

  override def getAgreement(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = authorize {
    val result: Future[Agreement] = for {
      organizationId <- getClaimFuture(contexts, ORGANIZATION_ID_CLAIM).flatMap(_.toFutureUUID)
      agreementUUID  <- agreementId.toFutureUUID
      agreement      <-
        agreementManagementService
          .getAgreementById(agreementUUID)(contexts)
          .ensure(Forbidden)(agr => organizationId == agr.producerId || organizationId == agr.consumerId)
    } yield agreement.toModel

    onComplete(result) {
      case Success(agr)                                              =>
        getAgreement200(agr)
      case Failure(Forbidden)                                        =>
        logger.error(s"The user has no access to the requested agreement ${agreementId}")
        getAgreement403(problemOf(StatusCodes.Forbidden, Forbidden))
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error(s"Error while getting agreement $agreementId - ${ex.getMessage}")
        getAgreement404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex) => internalServerError(s"Error while getting agreement - ${ex.getMessage}")
    }
  }

  override def getAgreements(
    producerId: Option[String],
    consumerId: Option[String],
    eserviceId: Option[String],
    descriptorId: Option[String],
    state: Option[String]
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAgreements: ToEntityMarshaller[Agreements],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {
    val result: Future[Agreements] = for {
      organizationId <- getClaimFuture(contexts, ORGANIZATION_ID_CLAIM)
      params         <- (producerId, consumerId) match {
        case (producer @ Some(_), None)                   => Future.successful((producer, Some(organizationId)))
        case (None, consumer @ Some(_))                   => Future.successful((Some(organizationId), consumer))
        case (Some(`organizationId`), consumer @ Some(_)) => Future.successful((Some(organizationId), consumer))
        case (producer @ Some(_), Some(`organizationId`)) => Future.successful((producer, Some(organizationId)))
        case (None, None)                                 => Future.failed(InvalidAgreementsInput)
        // TODO! case (Some(x), Some(y)) if x === y
        case _                                            => Future.failed(Forbidden)
      }
      (prod, cons) = params
      agreementState <- state.traverse(AgreementManagementApiAgreementState.fromValue(_).toFuture)
      rawAgreements  <- agreementManagementService.getAgreements(prod, cons, eserviceId, descriptorId, agreementState)(
        contexts
      )
      agreements = rawAgreements.map(_.toModel)
    } yield Agreements(agreements)

    onComplete(result) {
      case Success(agreements)             => getAgreements200(agreements)
      case Failure(InvalidAgreementsInput) =>
        logger.error(s"Error while getting agreements - ${InvalidAgreementsInput.getMessage}")
        getAgreements400(problemOf(StatusCodes.BadRequest, InvalidAgreementsInput))
      case Failure(Forbidden)              =>
        logger.error("The user has no access to the requested agreements")
        getAgreements403(problemOf(StatusCodes.Forbidden, Forbidden))
      case Failure(ex)                     => internalServerError(s"Error while getting agreements - ${ex.getMessage}")
    }
  }

  override def getAttribute(attributeId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {
    val result: Future[Attribute] = for {
      attributeUUID <- attributeId.toFutureUUID
      rawAttribute  <- attributeRegistryManagementService.getAttributeById(attributeUUID)(contexts)
      attribute     <- rawAttribute.toModel.toFuture
    } yield attribute

    onComplete(result) {
      case Success(attribute)                                        =>
        getAttribute200(attribute)
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error(s"Error while getting attribute $attributeId - ${ex.getMessage}")
        getAttribute404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex) => internalServerError(s"Error while getting attribute - ${ex.getMessage}")
    }
  }

  override def getEService(eserviceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = authorize {
    val result: Future[EService] = for {
      eserviceUUID <- eserviceId.toFutureUUID
      eservice     <- catalogManagementService.getEService(eserviceUUID)(contexts)
      descriptor   <- eservice.descriptors
        .find(_.id.toString == descriptorId)
        .toFuture(EServiceDescriptorNotFound(eserviceId, descriptorId))
    } yield eservice.toModel(descriptor)

    onComplete(result) {
      case Success(eservice)                                         =>
        getEService200(eservice)
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error(s"Error while getting eservice $eserviceId - ${ex.getMessage}")
        getEService404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex: EServiceDescriptorNotFound)                   =>
        logger.error(ex.getMessage)
        getEService404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex) => internalServerError(s"Error while getting eservice - ${ex.getMessage}")
    }
  }

  override def getOrganization(organizationId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerOrganization: ToEntityMarshaller[Organization],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {
    val result: Future[Organization] = for {
      organizationUUID <- organizationId.toFutureUUID
      organization     <- partyManagementService.getInstitution(organizationUUID)
    } yield organization.toModel

    onComplete(result) {
      case Success(organization)                                     => getOrganization200(organization)
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error(s"Error while getting organization $organizationId - ${ex.getMessage}")
        getOrganization404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex) => internalServerError(s"Error while getting organization - ${ex.getMessage}")
    }
  }

  override def getAgreementAttributes(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttributes: ToEntityMarshaller[Attributes],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {

    val result: Future[Attributes] = for {
      organizationId <- getClaimFuture(contexts, ORGANIZATION_ID_CLAIM).flatMap(_.toFutureUUID)
      agreementUUID  <- agreementId.toFutureUUID
      rawAgreement   <-
        agreementManagementService
          .getAgreementById(agreementUUID)(contexts)
          .ensure(Forbidden)(agr => organizationId == agr.producerId || organizationId == agr.consumerId)

      eservice <- catalogManagementService.getEService(rawAgreement.eserviceId)(contexts)

      attributeValidityStates = eservice.attributeUUIDSummary(
        certifiedFromParty = Set.empty,
        verifiedFromAgreement = rawAgreement.verifiedAttributes.toSet,
        declaredFromAgreement = Set.empty
      )

    } yield Attributes(attributeValidityStates)

    onComplete(result) {
      case Success(agr)                                              => getAgreementAttributes200(agr)
      case Failure(Forbidden)                                        =>
        logger.error(s"The user has no access to the requested attributes for agreement $agreementId")
        getAgreementAttributes403(problemOf(StatusCodes.Forbidden, Forbidden))
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error(s"Error while getting attributes for agreement $agreementId - ${ex.getMessage}")
        getAgreementAttributes404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex)                                               =>
        internalServerError(s"Error while getting attributes for agreement $agreementId - ${ex.getMessage}")
    }
  }

  override def getAgreementByPurpose(purposeId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = authorize {
    val result: Future[Agreement] = for {
      organizationId <- getClaimFuture(contexts, ORGANIZATION_ID_CLAIM).flatMap(_.toFutureUUID)
      purposeUUID    <- purposeId.toFutureUUID
      purpose        <- purposeManagementService.getPurpose(purposeUUID)(contexts)
      agreement      <- agreementManagementService
        .getActiveOrSuspendedAgreementByConsumerAndEserviceId(purpose.consumerId, purpose.eserviceId)
        .ensure(Forbidden)(a => a.consumerId == organizationId || a.producerId == organizationId)
    } yield agreement.toModel

    onComplete(result) {
      case Success(agreement)                                        => getAgreementByPurpose200(agreement)
      case Failure(Forbidden)                                        =>
        logger.error(s"The user has no access to the requested agreement for purpose $purposeId")
        getAgreementByPurpose403(problemOf(StatusCodes.Forbidden, Forbidden))
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error(s"Error while getting the requested agreement for purpose $purposeId - ${ex.getMessage}")
        getAgreementByPurpose404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex)                                               =>
        internalServerError(s"Error while getting the requested agreement for purpose $purposeId - ${ex.getMessage}")
    }
  }

  override def getPurpose(purposeId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurpose: ToEntityMarshaller[Purpose],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {

    def validatePurposeIfSubjectIsProducer(
      subject: UUID,
      purpose: PurposeManagementApiPurpose
    ): Future[PurposeManagementApiPurpose] =
      catalogManagementService
        .getEService(purpose.eserviceId)(contexts)
        .map(_.producerId == subject)
        .ifM(Future.successful(purpose), Future.failed(Forbidden))

    def getPurposeIfAuthorized(organizationId: UUID, purposeId: UUID): Future[PurposeManagementApiPurpose] =
      purposeManagementService
        .getPurpose(purposeId)(contexts)
        .flatMap(purpose =>
          if (purpose.consumerId == organizationId) Future.successful(purpose)
          else validatePurposeIfSubjectIsProducer(organizationId, purpose)
        )

    val result: Future[Purpose] = for {
      organizationUUID     <- getClaimFuture(contexts, ORGANIZATION_ID_CLAIM).flatMap(_.toFutureUUID)
      purposeUUID          <- purposeId.toFutureUUID
      purpose              <- getPurposeIfAuthorized(organizationUUID, purposeUUID)
      actualPurposeVersion <- purpose.toModel.toFuture
    } yield actualPurposeVersion

    onComplete(result) {
      case Success(agr)                                              => getPurpose200(agr)
      case Failure(Forbidden)                                        =>
        logger.error(s"The user has no access to the requested purpose $purposeId")
        getPurpose403(problemOf(StatusCodes.Forbidden, Forbidden))
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error(s"Error while getting the requested purpose $purposeId - ${ex.getMessage}")
        getPurpose404(problemOf(StatusCodes.NotFound, ex))
      case Failure(e: MissingActivePurposeVersion)                   =>
        logger.error(s"Unable to find an active version of purpose $purposeId")
        getPurpose404(problemOf(StatusCodes.NotFound, e))
      case Failure(ex)                                               =>
        internalServerError(s"Error while getting the requested purpose $purposeId - ${ex.getMessage}")
    }
  }

  override def getAgreementPurposes(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurposes: ToEntityMarshaller[Purposes],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {

    val result: Future[Purposes] = for {
      organizationUUID <- getClaimFuture(contexts, ORGANIZATION_ID_CLAIM).flatMap(_.toFutureUUID)
      agreementUUID    <- agreementId.toFutureUUID
      agreement        <- agreementManagementService
        .getAgreementById(agreementUUID)(contexts)
        .ensure(Forbidden)(a => a.consumerId == organizationUUID || a.producerId == organizationUUID)
      clientPurposes   <- purposeManagementService.getPurposes(agreement.eserviceId, agreement.consumerId)(contexts)
      purposes         <- clientPurposes.toModel.toFuture
    } yield purposes

    onComplete(result) {
      case Success(purposes)                                         => getAgreementPurposes200(purposes)
      case Failure(e: MissingActivePurposesVersions)                 =>
        logger.error(
          s"Unable to find active or suspended versions for purposes ${e.purposesIds.mkString(", ")} in the agreement $agreementId"
        )
        getAgreementPurposes200(Purposes(purposes = Seq.empty))
      case Failure(Forbidden)                                        =>
        logger.error(s"The user has no access to the requested agreement $agreementId")
        getPurpose403(problemOf(StatusCodes.Forbidden, Forbidden))
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error(s"Error while getting the requested purposes for agreement $agreementId - ${ex.getMessage}")
        getPurpose404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex)                                               =>
        internalServerError(s"Error while getting the requested purposes for agreement $agreementId - ${ex.getMessage}")
    }
  }

  override def getClient(clientId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerClient: ToEntityMarshaller[Client]
  ): Route = authorize {

    def isAllowed(client: AuthorizationManagementApiClient, organizationId: UUID): Future[Unit] = {
      if (client.consumerId == organizationId)
        Future.successful(())
      else
        client.purposes
          .findM(purpose =>
            catalogManagementService
              .getEService(purpose.states.eservice.eserviceId)(contexts)
              .map(_.producerId == organizationId)
          )
          .ensure(Forbidden)(_.nonEmpty)
          .as(())
    }

    val result: Future[Client] = for {
      organizationId <- getClaimFuture(contexts, ORGANIZATION_ID_CLAIM).flatMap(_.toFutureUUID)
      clientUUID     <- clientId.toFutureUUID
      client         <- authorizationManagementService.getClientById(clientUUID)(contexts)
      _              <- isAllowed(client, organizationId)
    } yield client.toModel

    onComplete(result) {
      case Success(client)                                           =>
        getClient200(client)
      case Failure(Forbidden)                                        =>
        logger.error(s"The user has no access to the requested client $clientId")
        getAgreement403(problemOf(StatusCodes.Forbidden, Forbidden))
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error(s"Error while getting client $clientId - ${ex.getMessage}")
        getAgreement404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex) => internalServerError(s"Error while getting client - ${ex.getMessage}")
    }
  }

  /**
   * Code: 200, Message: Messages, DataType: Messages
   * Code: 400, Message: Bad request, DataType: Problem
   * Code: 401, Message: Unauthorized, DataType: Problem
   * Code: 404, Message: Events not found, DataType: Problem
   */
  override def getEventsFromId(lastEventId: Long, limit: Int)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEvents: ToEntityMarshaller[Events],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {
    val result: Future[Events] = for {
      events        <- notifierService.getEvents(lastEventId, limit)(contexts)
      gatewayEvents <- events.toModel.toFuture
    } yield gatewayEvents

    onComplete(result) {
      case Success(messages)                                         => getEventsFromId200(messages)
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error(s"Error while getting the requested messages - ${ex.getMessage}")
        getEventsFromId404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex)                                               =>
        internalServerError(s"Error while getting the requested messages - ${ex.getMessage}")
    }
  }
}
