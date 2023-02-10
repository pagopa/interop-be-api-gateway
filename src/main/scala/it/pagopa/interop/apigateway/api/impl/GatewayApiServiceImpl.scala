package it.pagopa.interop.apigateway.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementmanagement.client.{model => AgreementManagementDependency}
import it.pagopa.interop.apigateway.api.GatewayApiService
import it.pagopa.interop.apigateway.api.impl.ResponseHandlers._
import it.pagopa.interop.apigateway.error.GatewayErrors._
import it.pagopa.interop.apigateway.model._
import it.pagopa.interop.apigateway.service._
import it.pagopa.interop.attributeregistrymanagement.client.model
import it.pagopa.interop.attributeregistrymanagement.client.model.{Attribute => AttributeManagementApiAttribute}
import it.pagopa.interop.authorizationmanagement.client.model.{Client => AuthorizationManagementApiClient}
import it.pagopa.interop.catalogmanagement.client.model.{
  EService => CatalogManagementEService,
  EServiceDescriptorState => CatalogManagementDescriptorState
}
import it.pagopa.interop.commons
import it.pagopa.interop.commons.jwt.M2M_ROLE
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getOrganizationIdFutureUUID
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.OperationForbidden
import it.pagopa.interop.purposemanagement.client.model.{Purpose => PurposeManagementApiPurpose}
import it.pagopa.interop.tenantmanagement.client.model.{Tenant => TenantManagementApiTenant}
import it.pagopa.interop.tenantprocess.client.model.{ExternalId, M2MAttributeSeed, M2MTenantSeed}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class GatewayApiServiceImpl(
  agreementManagementService: AgreementManagementService,
  authorizationManagementService: AuthorizationManagementService,
  catalogManagementService: CatalogManagementService,
  attributeRegistryManagementService: AttributeRegistryManagementService,
  partyRegistryProxyService: PartyRegistryProxyService,
  purposeManagementService: PurposeManagementService,
  notifierService: NotifierService,
  tenantProcessService: TenantProcessService,
  tenantManagementService: TenantManagementService
)(implicit ec: ExecutionContext)
    extends GatewayApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  private[this] def authorize(route: => Route)(implicit contexts: Seq[(String, String)]): Route =
    commons.jwt.authorize(M2M_ROLE)(route)

  override def getAgreement(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = authorize {
    val operationLabel = s"Retrieving agreement $agreementId"
    logger.info(operationLabel)

    val result: Future[Agreement] = for {
      agreementUUID <- agreementId.toFutureUUID
      agreement     <- agreementManagementService.getAgreementById(agreementUUID)
      apiModel      <- agreement.toModel.toFuture
    } yield apiModel

    onComplete(result) {
      getAgreementResponse[Agreement](operationLabel, agreementId)(getAgreement200)
    }
  }

  override def upsertTenant(origin: String, externalId: String, code: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {
    val operationLabel = s"Upserting tenant with externalId ($origin,$externalId) with attribute $code"
    logger.info(operationLabel)

    val result: Future[Unit] = for {
      institution <- partyRegistryProxyService.getInstitutionByExternalId(origin, externalId)
      updated     <- tenantProcessService
        .upsertTenant(m2mTenantSeedFromApi(origin, externalId, institution.description)(code))
        .void
    } yield updated

    onComplete(result) {
      upsertTenantResponse[Unit](operationLabel)(_ => upsertTenant204)
    }
  }

  override def revokeTenantAttribute(origin: String, externalId: String, code: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {
    val operationLabel = s"Revoking attribute $code of tenant ($origin,$externalId)"
    logger.info(operationLabel)

    onComplete(tenantProcessService.revokeAttribute(origin, externalId, code)) {
      revokeTenantAttributeResponse[Unit](operationLabel)(_ => revokeTenantAttribute204)
    }
  }

  override def getAgreements(
    producerId: Option[String],
    consumerId: Option[String],
    eServiceId: Option[String],
    descriptorId: Option[String],
    states: String
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAgreements: ToEntityMarshaller[Agreements],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {
    import AgreementManagementDependency.AgreementState._
    val operationLabel =
      s"Retrieving agreements for producerId $producerId consumerId $consumerId eServiceId $eServiceId descriptorId $descriptorId states $states"
    logger.info(operationLabel)

    val result: Future[Agreements] = for {
      // Safe condition to reduce data volume, it can be removed once the pagination will be used
      _               <- Future.failed(ProducerAndConsumerParamMissing).whenA(producerId.isEmpty && consumerId.isEmpty)
      agreementStates <- parseArrayParameters(states)
        .traverse(AgreementManagementDependency.AgreementState.fromValue)
        .toFuture
      safeAgreementStates =
        if (agreementStates.nonEmpty) agreementStates
        else List(PENDING, ACTIVE, SUSPENDED, ARCHIVED, MISSING_CERTIFIED_ATTRIBUTES)
      rawAgreements <- agreementManagementService.getAgreements(
        producerId,
        consumerId,
        eServiceId,
        descriptorId,
        safeAgreementStates
      )(contexts)
      agreements    <- rawAgreements.traverse(_.toModel).toFuture
    } yield Agreements(agreements)

    onComplete(result) {
      getAgreementsResponse[Agreements](operationLabel)(getAgreements200)
    }
  }

  override def getAttribute(attributeId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {
    val operationLabel = s"Retrieving attribute $attributeId"
    logger.info(operationLabel)

    val result: Future[Attribute] = for {
      attributeUUID <- attributeId.toFutureUUID
      attribute     <- attributeRegistryManagementService.getAttributeById(attributeUUID)(contexts)
    } yield attribute.toModel

    onComplete(result) {
      getAttributeResponse[Attribute](operationLabel)(getAttribute200)
    }
  }

  override def getEService(eServiceId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = authorize {
    val operationLabel = s"Retrieving EService $eServiceId"
    logger.info(operationLabel)

    val result: Future[EService] = for {
      eServiceUUID <- eServiceId.toFutureUUID
      eService     <- catalogManagementService.getEService(eServiceUUID)(contexts)
      apiEService  <- enhanceEService(eService)
    } yield apiEService

    onComplete(result) {
      getEServiceResponse[EService](operationLabel)(getEService200)
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
    val operationLabel =
      s"Retrieving Organization EServices for origin $origin externalId $externalId attributeOrigin $attributeOrigin attributeCode $attributeCode"
    logger.info(operationLabel)

    val result: Future[EServices] = for {
      tenant    <- tenantManagementService.getTenantByExternalId(origin, externalId)
      attribute <- attributeRegistryManagementService.getAttributeByOriginAndCode(attributeOrigin, attributeCode)
      eServices <- catalogManagementService.getEServices(tenant.id, attribute.id)
      allowedEServices = eServices.filter(_.descriptors.nonEmpty)
      apiEServices <- Future.traverse(allowedEServices)(enhanceEService)
    } yield EServices(apiEServices)

    onComplete(result) {
      getOrganizationEServicesResponse[EServices](operationLabel)(getOrganizationEServices200)
    }
  }

  override def getEServiceDescriptor(eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEServiceDescriptor: ToEntityMarshaller[EServiceDescriptor],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {
    val operationLabel = s"Retrieving Descriptor $descriptorId of EService $eServiceId"
    logger.info(operationLabel)

    val result: Future[EServiceDescriptor] = for {
      eServiceUUID <- eServiceId.toFutureUUID
      eService     <- catalogManagementService.getEService(eServiceUUID)(contexts)
      descriptor   <- eService.descriptors
        .find(_.id.toString == descriptorId)
        .toFuture(EServiceDescriptorNotFound(eServiceId, descriptorId))
      result       <- descriptor.toModel.toFuture
    } yield result

    onComplete(result) {
      getEServiceDescriptorResponse[EServiceDescriptor](operationLabel, eServiceId, descriptorId)(
        getEServiceDescriptor200
      )
    }
  }

  override def getEServiceDescriptors(eServiceId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEServiceDescriptors: ToEntityMarshaller[EServiceDescriptors],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {
    val operationLabel = s"Retrieving Descriptors of EService $eServiceId"
    logger.info(operationLabel)

    val result: Future[EServiceDescriptors] = for {
      eServiceUUID <- eServiceId.toFutureUUID
      eService     <- catalogManagementService.getEService(eServiceUUID)(contexts)
      descriptors  <- eService.descriptors
        .filter(_.state != CatalogManagementDescriptorState.DRAFT)
        .traverse(_.toModel)
        .toFuture
    } yield EServiceDescriptors(descriptors = descriptors)

    onComplete(result) {
      getEServiceDescriptorsResponse[EServiceDescriptors](operationLabel)(getEServiceDescriptors200)
    }
  }

  override def getOrganization(tenantId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerOrganization: ToEntityMarshaller[Organization],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {
    val operationLabel = s"Retrieving Tenant $tenantId"
    logger.info(operationLabel)

    val result: Future[Organization] = for {
      tenantUUID <- tenantId.toFutureUUID
      tenant     <- tenantManagementService.getTenantById(tenantUUID)
      category   <- extractCategoryIpa(tenant)
    } yield tenant.toModel(category)

    onComplete(result) {
      getOrganizationResponse[Organization](operationLabel)(getOrganization200)
    }
  }

  override def getAgreementAttributes(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttributes: ToEntityMarshaller[Attributes],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {
    val operationLabel = s"Retrieving Attributes of Agreement $agreementId"
    logger.info(operationLabel)

    val result: Future[Attributes] = for {
      agreementUUID <- agreementId.toFutureUUID
      rawAgreement  <- agreementManagementService.getAgreementById(agreementUUID)
      tenant        <- tenantManagementService.getTenantById(rawAgreement.consumerId)
      verifiedAttributes  = rawAgreement.verifiedAttributes
        .flatMap(a => tenant.attributes.mapFilter(_.verified).filter(_.id == a.id))
        .toSet
      declaredAttributes  = rawAgreement.declaredAttributes
        .flatMap(a => tenant.attributes.mapFilter(_.declared).filter(_.id == a.id))
        .toSet
      certifiedAttributes = rawAgreement.certifiedAttributes
        .flatMap(a => tenant.attributes.mapFilter(_.certified).filter(_.id == a.id))
        .toSet
    } yield Attributes(
      verified = verifiedAttributes.map(_.toAgreementModel),
      declared = declaredAttributes.map(_.toAgreementModel),
      certified = certifiedAttributes.map(_.toAgreementModel)
    )

    onComplete(result) {
      getAgreementAttributesResponse[Attributes](operationLabel)(getAgreementAttributes200)
    }
  }

  override def getAgreementByPurpose(purposeId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = authorize {
    val operationLabel = s"Retrieving Agreement by Purpose $purposeId"
    logger.info(operationLabel)

    val result: Future[Agreement] = for {
      purposeUUID <- purposeId.toFutureUUID
      purpose     <- purposeManagementService.getPurpose(purposeUUID)(contexts)
      agreement   <- agreementManagementService.getActiveOrSuspendedAgreementByConsumerAndEserviceId(
        purpose.consumerId,
        purpose.eserviceId
      )
      apiModel    <- agreement.toModel.toFuture
    } yield apiModel

    onComplete(result) {
      getAgreementByPurposeResponse[Agreement](operationLabel)(getAgreementByPurpose200)
    }
  }

  override def getPurpose(purposeId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurpose: ToEntityMarshaller[Purpose],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {
    val operationLabel = s"Retrieving Purpose $purposeId"
    logger.info(operationLabel)

    def validatePurposeIfSubjectIsProducer(
      subject: UUID,
      purpose: PurposeManagementApiPurpose
    ): Future[PurposeManagementApiPurpose] =
      catalogManagementService
        .getEService(purpose.eserviceId)(contexts)
        .map(_.producerId == subject)
        .ifM(Future.successful(purpose), Future.failed(OperationForbidden))

    def getPurposeIfAuthorized(organizationId: UUID, purposeId: UUID): Future[PurposeManagementApiPurpose] =
      purposeManagementService
        .getPurpose(purposeId)(contexts)
        .flatMap(purpose =>
          if (purpose.consumerId == organizationId) Future.successful(purpose)
          else validatePurposeIfSubjectIsProducer(organizationId, purpose)
        )

    val result: Future[Purpose] = for {
      organizationUUID     <- getOrganizationIdFutureUUID(contexts)
      purposeUUID          <- purposeId.toFutureUUID
      purpose              <- getPurposeIfAuthorized(organizationUUID, purposeUUID)
      actualPurposeVersion <- purpose.toModel.toFuture
    } yield actualPurposeVersion

    onComplete(result) {
      getPurposeResponse[Purpose](operationLabel)(getPurpose200)
    }
  }

  override def getAgreementPurposes(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurposes: ToEntityMarshaller[Purposes],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {
    val operationLabel = s"Retrieving Purposes for Agreement $agreementId"
    logger.info(operationLabel)

    val result: Future[Purposes] = for {
      agreementUUID  <- agreementId.toFutureUUID
      agreement      <- agreementManagementService.getAgreementById(agreementUUID)
      clientPurposes <- purposeManagementService.getPurposes(agreement.eserviceId, agreement.consumerId)(contexts)
      purposes       <- clientPurposes.toModel.toFuture
    } yield purposes

    onComplete(result) {
      getAgreementPurposesResponse(operationLabel)(getAgreementPurposes200, Purposes(purposes = Seq.empty))
    }
  }

  override def createCertifiedAttribute(attributeSeed: AttributeSeed)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {
    val operationLabel = s"Creating certified attribute with code ${attributeSeed.code}"
    logger.info(operationLabel)

    val result: Future[Attribute] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
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
      createCertifiedAttributeResponse[Attribute](operationLabel)(createCertifiedAttribute200)
    }
  }

  override def getClient(clientId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerClient: ToEntityMarshaller[Client]
  ): Route = authorize {
    val operationLabel = s"Retrieving Client $clientId"
    logger.info(operationLabel)

    // TODO Remove if unnecessary, otherwise move to Authorization Process
    def isAllowed(client: AuthorizationManagementApiClient, organizationId: UUID): Future[Unit] =
      if (client.consumerId == organizationId) Future.unit
      else
        client.purposes
          .findM(purpose =>
            catalogManagementService
              .getEService(purpose.states.eservice.eserviceId)(contexts)
              .map(_.producerId == organizationId)
          )
          .ensure(OperationForbidden)(_.nonEmpty)
          .void

    val result: Future[Client] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      clientUUID     <- clientId.toFutureUUID
      client         <- authorizationManagementService.getClientById(clientUUID)(contexts)
      _              <- isAllowed(client, organizationId)
    } yield client.toModel

    onComplete(result) {
      getClientResponse[Client](operationLabel)(getClient200)
    }
  }

  override def getEventsFromId(lastEventId: Long, limit: Int)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEvents: ToEntityMarshaller[Events],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {
    val operationLabel = s"Retrieving Events lastEventId $lastEventId limit $limit"
    logger.info(operationLabel)

    val result: Future[Events] = notifierService.getEvents(lastEventId, limit).map(_.toModel)

    onComplete(result) {
      getEventsFromIdResponse[Events](operationLabel)(getEventsFromId200)
    }
  }

  override def getEservicesEventsFromId(lastEventId: Long, limit: Int)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEvents: ToEntityMarshaller[Events],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {
    val operationLabel = s"Retrieving EServices Events lastEventId $lastEventId limit $limit"
    logger.info(operationLabel)

    val result: Future[Events] = notifierService.getAllOrganizationEvents(lastEventId, limit).map(_.toModel)

    onComplete(result) {
      getEservicesEventsFromIdResponse[Events](operationLabel)(getEventsFromId200)
    }
  }

  def m2mTenantSeedFromApi(origin: String, externalId: String, name: String)(code: String): M2MTenantSeed =
    M2MTenantSeed(ExternalId(origin, externalId), M2MAttributeSeed(code) :: Nil, name)

  def enhanceEService(eService: CatalogManagementEService)(implicit contexts: Seq[(String, String)]): Future[EService] =
    for {
      tenant           <- tenantManagementService.getTenantById(eService.producerId)
      latestDescriptor <- eService.latestAvailableDescriptor
      state            <- latestDescriptor.state.toModel.toFuture
      allAttributesIds = eService.attributes.allIds
      attributes <- attributeRegistryManagementService.getBulkAttributes(allAttributesIds)
      attributes <- eService.attributes.toModel(attributes.attributes).toFuture
      category   <- extractCategoryIpa(tenant)
    } yield EService(
      id = eService.id,
      producer = tenant.toModel(category),
      name = eService.name,
      version = latestDescriptor.version,
      description = eService.description,
      technology = eService.technology.toModel,
      attributes = attributes,
      state = state,
      serverUrls = latestDescriptor.serverUrls
    )
  private def extractCategoryIpa(
    tenant: TenantManagementApiTenant
  )(implicit contexts: Seq[(String, String)]): Future[String] = {
    val certified: Seq[UUID] = tenant.attributes.flatMap(_.certified.map(_.id))
    Future.traverse(certified)(attributeRegistryManagementService.getAttributeById).map(extractCategoryIpa)
  }

  /* it has been implemented in this way
    in order to maintain backwards compatibility
    with the current exposed model which requires the IPA category
   */
  private def extractCategoryIpa(attributes: Seq[AttributeManagementApiAttribute]): String =
    attributes.find(_.origin == "IPA".some).map(_.name).getOrElse("Unknown")
}
