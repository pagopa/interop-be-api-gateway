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
import it.pagopa.interop.catalogmanagement.client.model.{EService => CatalogManagementEService}
import it.pagopa.interop.commons.jwt.{M2M_ROLE, authorizeInterop, hasPermissions}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils._
import it.pagopa.interop.commons.utils.ORGANIZATION_ID_CLAIM
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.OperationForbidden
import it.pagopa.interop.purposemanagement.client.model.{Purpose => PurposeManagementApiPurpose}
import it.pagopa.interop.tenantprocess.client.model.{M2MTenantSeed, M2MAttributeSeed, ExternalId}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import it.pagopa.interop.attributeregistrymanagement.client.model

final case class GatewayApiServiceImpl(
  partyManagementService: PartyManagementService,
  agreementManagementService: AgreementManagementService,
  authorizationManagementService: AuthorizationManagementService,
  catalogManagementService: CatalogManagementService,
  attributeRegistryManagementService: AttributeRegistryManagementService,
  purposeManagementService: PurposeManagementService,
  notifierService: NotifierService,
  tenantProcessService: TenantProcessService,
  tenantManagementService: TenantManagementService
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
        logger.error(s"The user has no access to the requested agreement $agreementId")
        getAgreement403(problemOf(StatusCodes.Forbidden, Forbidden))
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error(s"Error while getting agreement $agreementId - ${ex.getMessage}")
        getAgreement404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex) => internalServerError(s"Error while getting agreement - ${ex.getMessage}")
    }
  }

  override def upsertTenant(origin: String, externalId: String, code: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {
    logger.info(s"Upserting tenant with extenalId ($origin,$externalId) and attribute $code")

    val result: Future[Unit] = tenantProcessService
      .upsertTenant(m2mTenantSeedFromApi(origin, externalId)(code))
      .void

    onComplete(result) {
      case Success(())                 => upsertTenant204
      case Failure(OperationForbidden) =>
        logger.error(
          s"Error while upserting tenant with extenalId ($origin,$externalId) and attribute $code - ${OperationForbidden.getMessage}"
        )
        getAgreement404(problemOf(StatusCodes.Forbidden, OperationForbidden))
      case Failure(ex)                 => internalServerError(s"Error while upserting tenant - ${ex.getMessage}")
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
      attribute     <- attributeRegistryManagementService.getAttributeById(attributeUUID)(contexts)
    } yield attribute.toModel

    onComplete(result) {
      case Success(attribute)                                        =>
        getAttribute200(attribute)
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error(s"Error while getting attribute $attributeId - ${ex.getMessage}")
        getAttribute404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex) => internalServerError(s"Error while getting attribute - ${ex.getMessage}")
    }
  }

  override def getEService(eServiceId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = authorize {
    val result: Future[EService] = for {
      eServiceUUID <- eServiceId.toFutureUUID
      eService     <- catalogManagementService.getEService(eServiceUUID)(contexts)
      apiEService  <- enhanceEService(eService)
    } yield apiEService

    onComplete(result) {
      case Success(eService)                                         =>
        getEService200(eService)
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error(s"Error while getting EService $eServiceId", ex)
        getEService404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex: MissingAvailableDescriptor)                   =>
        logger.error(s"Error while getting EService $eServiceId", ex)
        getEService400(problemOf(StatusCodes.BadRequest, ex))
      case Failure(ex)                                               =>
        internalServerError(s"Error while getting EService $eServiceId - ${ex.getMessage}")
    }
  }

  override def getOrganizationEServices(
    attributeOrigin: String,
    attributeCode: String,
    origin: String,
    externalId: String
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEServices: ToEntityMarshaller[EServices],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[EServices] = for {
      tenant       <- tenantManagementService.getTenantByExternalId(origin, externalId)
      attribute    <- attributeRegistryManagementService.getAttributeByOriginAndCode(attributeOrigin, attributeCode)
      eServices    <- catalogManagementService.getEServices(tenant.id, attribute.id)
      apiEServices <- Future.traverse(eServices)(enhanceEService)
    } yield EServices(apiEServices)

    onComplete(result) {
      case Success(eServices)                                        =>
        getOrganizationEServices200(eServices)
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error(s"Error while getting Organization EServices for Origin $origin and Code $externalId", ex)
        getOrganizationEServices404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex)                                               =>
        internalServerError(
          s"Error while getting Organization EServices for Origin $origin and Code $externalId - ${ex.getMessage}"
        )
    }
  }

  override def getEServiceDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEServiceDescriptor: ToEntityMarshaller[EServiceDescriptor],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {
    val result: Future[EServiceDescriptor] = for {
      eServiceUUID <- eServiceId.toFutureUUID
      eService     <- catalogManagementService.getEService(eServiceUUID)(contexts)
      descriptor   <- eService.descriptors
        .find(_.id.toString == descriptorId)
        .toFuture(EServiceDescriptorNotFound(eServiceId, descriptorId))
      result       <- descriptor.toModel.toFuture
    } yield result

    onComplete(result) {
      case Success(descriptor)                                       =>
        getEServiceDescriptor200(descriptor)
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error(s"Error while getting EService $eServiceId and Descriptor $descriptorId", ex)
        getEServiceDescriptor404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex: EServiceDescriptorNotFound)                   =>
        logger.error(s"Error while getting EService $eServiceId and Descriptor $descriptorId", ex)
        getEServiceDescriptor404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex: UnexpectedDescriptorState)                    =>
        logger.error(s"Error while getting EService $eServiceId and Descriptor $descriptorId", ex)
        getEServiceDescriptor400(problemOf(StatusCodes.BadRequest, ex))
      case Failure(ex)                                               =>
        internalServerError(s"Error while getting EService $eServiceId and Descriptor $descriptorId - ${ex.getMessage}")
    }
  }

  override def getEServiceDescriptors(eServiceId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEServiceDescriptors: ToEntityMarshaller[EServiceDescriptors],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {
    val result: Future[EServiceDescriptors] = for {
      eServiceUUID <- eServiceId.toFutureUUID
      eService     <- catalogManagementService.getEService(eServiceUUID)(contexts)
      descriptors  <- eService.descriptors.traverse(_.toModel).toFuture
    } yield EServiceDescriptors(descriptors = descriptors)

    onComplete(result) {
      case Success(descriptor)                                       =>
        getEServiceDescriptors200(descriptor)
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error(s"Error while getting descriptors for EService $eServiceId", ex)
        getEServiceDescriptors404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex: UnexpectedDescriptorState)                    =>
        logger.error(s"Error while getting descriptors for EService $eServiceId", ex)
        getEServiceDescriptors400(problemOf(StatusCodes.BadRequest, ex))
      case Failure(ex)                                               =>
        internalServerError(s"Error while getting descriptors for EService $eServiceId - ${ex.getMessage}")
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
      apiOrganization  <- organization.toModel.toFuture
    } yield apiOrganization

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

  override def createCertifiedAttribute(attributeSeed: AttributeSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route =
    authorize {

      val result: Future[Attribute] = for {
        organizationId <- getClaimFuture(contexts, ORGANIZATION_ID_CLAIM).flatMap(_.toFutureUUID)
        certifierId    <- tenantProcessService
          .getTenant(organizationId)
          .map(_.features.collectFirstSome(_.certifier).map(_.certifierId))
          .flatMap(_.toFuture(OrganizationIsNotACertifier(organizationId)))
        attribute      <- attributeRegistryManagementService
          .createAttribute(
            model.AttributeSeed(
              name = attributeSeed.name,
              origin = certifierId.some,
              code = attributeSeed.code.some,
              kind = model.AttributeKind.CERTIFIED,
              description = attributeSeed.description
            )
          )
      } yield Attribute(id = attribute.id, attribute.name, kind = AttributeKind.CERTIFIED)

      onComplete(result) {
        case Success(attribute)                             =>
          createCertifiedAttribute200(attribute)
        case Failure(ex @ OrganizationIsNotACertifier(org)) =>
          logger.error(s"The tenant $org is not a certifier and cannot create attributes")
          createCertifiedAttribute403(problemOf(StatusCodes.Forbidden, ex))
        case Failure(ex) => internalServerError(s"Error while creating attribute - ${ex.getMessage}")
      }
    }

  override def getClient(clientId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerClient: ToEntityMarshaller[Client]
  ): Route = authorize {

    def isAllowed(client: AuthorizationManagementApiClient, organizationId: UUID): Future[Unit] =
      if (client.consumerId == organizationId) Future.unit
      else
        client.purposes
          .findM(purpose =>
            catalogManagementService
              .getEService(purpose.states.eservice.eserviceId)(contexts)
              .map(_.producerId == organizationId)
          )
          .ensure(Forbidden)(_.nonEmpty)
          .void

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

  def m2mTenantSeedFromApi(origin: String, externalId: String)(code: String): M2MTenantSeed =
    M2MTenantSeed(ExternalId(origin, externalId), M2MAttributeSeed(code) :: Nil)

  def enhanceEService(eService: CatalogManagementEService)(implicit contexts: Seq[(String, String)]): Future[EService] =
    for {
      producer         <- partyManagementService.getInstitution(eService.producerId)
      latestDescriptor <- eService.latestAvailableDescriptor
      state            <- latestDescriptor.state.toModel.toFuture
      allAttributesIds = eService.attributes.allIds
      attributes  <- attributeRegistryManagementService.getBulkAttributes(allAttributesIds)
      apiProducer <- producer.toModel.toFuture
      attributes  <- eService.attributes.toModel(attributes.attributes).toFuture
    } yield EService(
      id = eService.id,
      producer = apiProducer,
      name = eService.name,
      version = latestDescriptor.version,
      description = eService.description,
      technology = eService.technology.toModel,
      attributes = attributes,
      state = state
    )

}
