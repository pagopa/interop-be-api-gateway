package it.pagopa.interop.apigateway.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import cats.syntax.all._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementprocess.client.{model => AgreementProcessDependency}
import it.pagopa.interop.apigateway.api.GatewayApiService
import it.pagopa.interop.apigateway.api.impl.ResponseHandlers._
import it.pagopa.interop.apigateway.error.GatewayErrors._
import it.pagopa.interop.apigateway.model._
import it.pagopa.interop.apigateway.service._
import it.pagopa.interop.attributeregistryprocess.client.model.CertifiedAttributeSeed
import it.pagopa.interop.attributeregistryprocess.client.model.{Attribute => AttributeProcessApiAttribute}
import it.pagopa.interop.authorizationprocess.client.model.{Client => AuthorizationProcessApiClient}
import it.pagopa.interop.catalogprocess.client.model.{
  EService => CatalogProcessEService,
  EServiceDescriptorState => CatalogProcessDescriptorState
}
import it.pagopa.interop.commons
import it.pagopa.interop.commons.cqrs.service.ReadModelService
import it.pagopa.interop.commons.jwt.M2M_ROLE
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getOrganizationIdFutureUUID
import it.pagopa.interop.commons.utils.OpenapiUtils.parseArrayParameters
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.OperationForbidden
import it.pagopa.interop.purposeprocess.client.model.{Purpose => PurposeProcessApiPurpose}
import it.pagopa.interop.tenantprocess.client.model.{Tenant => TenantProcessApiTenant}
import it.pagopa.interop.tenantprocess.client.model.{ExternalId, M2MAttributeSeed, M2MTenantSeed}
import org.mongodb.scala.model.Filters

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final case class GatewayApiServiceImpl(
  agreementProcessService: AgreementProcessService,
  authorizationProcessService: AuthorizationProcessService,
  catalogProcessService: CatalogProcessService,
  attributeRegistryProcessService: AttributeRegistryProcessService,
  partyRegistryProxyService: PartyRegistryProxyService,
  purposeProcessService: PurposeProcessService,
  notifierService: NotifierService,
  tenantProcessService: TenantProcessService
)(implicit ec: ExecutionContext, readModel: ReadModelService)
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
      agreement     <- agreementProcessService.getAgreementById(agreementUUID)
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
      institution <- partyRegistryProxyService.getInstitutionByExternalId(origin, externalId).recoverWith {
        case _: InstitutionNotFound =>
          partyRegistryProxyService.getAOOByExternalId(origin, externalId).recoverWith { case _: InstitutionNotFound =>
            partyRegistryProxyService.getUOByExternalId(origin, externalId)
          }
      }
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
    import AgreementProcessDependency.AgreementState._
    val operationLabel =
      s"Retrieving agreements for producerId $producerId consumerId $consumerId eServiceId $eServiceId descriptorId $descriptorId states $states"
    logger.info(operationLabel)

    val result: Future[Agreements] = for {
      producerUuid    <- Future.traverse(producerId.toList)(_.toFutureUUID)
      consumerUuid    <- Future.traverse(consumerId.toList)(_.toFutureUUID)
      eServiceUuid    <- Future.traverse(eServiceId.toList)(_.toFutureUUID)
      descriptorUuid  <- Future.traverse(descriptorId.toList)(_.toFutureUUID)
      // Safe condition to reduce data volume, it can be removed once the pagination will be used
      _               <- Future.failed(ProducerAndConsumerParamMissing).whenA(producerId.isEmpty && consumerId.isEmpty)
      agreementStates <- parseArrayParameters(states)
        .traverse(AgreementProcessDependency.AgreementState.fromValue)
        .toFuture
      safeAgreementStates =
        if (agreementStates.nonEmpty) agreementStates
        else List(PENDING, ACTIVE, SUSPENDED, ARCHIVED, MISSING_CERTIFIED_ATTRIBUTES)
      rawAgreements <- agreementProcessService.getAllAgreements(
        producerUuid.headOption,
        consumerUuid.headOption,
        eServiceUuid.headOption,
        descriptorUuid.headOption,
        safeAgreementStates
      )
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
      attribute     <- attributeRegistryProcessService.getAttributeById(attributeUUID)
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
      eService     <- catalogProcessService.getEServiceById(eServiceUUID)
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
      tenant    <- tenantProcessService.getTenantByExternalId(origin, externalId)
      attribute <- attributeRegistryProcessService.getAttributeByOriginAndCode(attributeOrigin, attributeCode)
      eServices <- catalogProcessService.getAllEServices(tenant.id, attribute.id)
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
      eService     <- catalogProcessService.getEServiceById(eServiceUUID)
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
      eService     <- catalogProcessService.getEServiceById(eServiceUUID)
      descriptors  <- eService.descriptors
        .filter(_.state != CatalogProcessDescriptorState.DRAFT)
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
      tenant     <- tenantProcessService.getTenantById(tenantUUID)
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
      rawAgreement  <- agreementProcessService.getAgreementById(agreementUUID)
      tenant        <- tenantProcessService.getTenantById(rawAgreement.consumerId)
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
      purpose     <- purposeProcessService.getPurpose(purposeUUID)
      agreement   <- agreementProcessService.getActiveOrSuspendedAgreementByConsumerAndEserviceId(
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
      purpose: PurposeProcessApiPurpose
    ): Future[PurposeProcessApiPurpose] =
      catalogProcessService
        .getEServiceById(purpose.eserviceId)
        .map(_.producerId == subject)
        .ifM(Future.successful(purpose), Future.failed(OperationForbidden))

    def getPurposeIfAuthorized(organizationId: UUID, purposeId: UUID): Future[PurposeProcessApiPurpose] =
      purposeProcessService
        .getPurpose(purposeId)
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
      agreement      <- agreementProcessService.getAgreementById(agreementUUID)
      clientPurposes <- purposeProcessService.getAllPurposes(agreement.eserviceId, agreement.consumerId)
      purposes       <- clientPurposes.traverse(_.toModel).toFuture
    } yield Purposes(purposes)

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

    val result: Future[Attribute] = attributeRegistryProcessService
      .createCertifiedAttribute(
        CertifiedAttributeSeed(
          name = attributeSeed.name,
          code = attributeSeed.code,
          description = attributeSeed.description
        )
      )
      .map(attribute => Attribute(id = attribute.id, attribute.name, kind = AttributeKind.CERTIFIED))

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
    def isAllowed(client: AuthorizationProcessApiClient, organizationId: UUID): Future[Unit] =
      if (client.consumerId == organizationId) Future.unit
      else
        client.purposes
          .findM(purpose =>
            catalogProcessService
              .getEServiceById(purpose.states.eservice.eserviceId)
              .map(_.producerId == organizationId)
          )
          .ensure(OperationForbidden)(_.nonEmpty)
          .void

    val result: Future[Client] = for {
      organizationId <- getOrganizationIdFutureUUID(contexts)
      clientUUID     <- clientId.toFutureUUID
      client         <- authorizationProcessService.getClientById(clientUUID)
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

  override def getKeysEventsFromId(lastEventId: Long, limit: Int)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerEvents: ToEntityMarshaller[Events],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {
    val operationLabel = s"Retrieving Keys Events lastEventId $lastEventId limit $limit"
    logger.info(operationLabel)

    val result: Future[Events] = notifierService.getKeysEvents(lastEventId, limit).map(_.toModel)

    onComplete(result) {
      getKeysEventsFromIdResponse[Events](operationLabel)(getKeysEventsFromId200)
    }
  }

  def m2mTenantSeedFromApi(origin: String, externalId: String, name: String)(code: String): M2MTenantSeed =
    M2MTenantSeed(ExternalId(origin, externalId), M2MAttributeSeed(code) :: Nil, name)

  def enhanceEService(eService: CatalogProcessEService)(implicit contexts: Seq[(String, String)]): Future[EService] =
    for {
      tenant           <- tenantProcessService.getTenantById(eService.producerId)
      latestDescriptor <- eService.latestAvailableDescriptor
      state            <- latestDescriptor.state.toModel.toFuture
      allAttributesIds = latestDescriptor.attributes.allIds
      attributes <- attributeRegistryProcessService.getAllBulkAttributes(allAttributesIds)
      attributes <- latestDescriptor.attributes.toModel(attributes).toFuture
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
    tenant: TenantProcessApiTenant
  )(implicit contexts: Seq[(String, String)]): Future[String] = {
    val certified: Seq[UUID] = tenant.attributes.flatMap(_.certified.map(_.id))
    Future.traverse(certified)(attributeRegistryProcessService.getAttributeById).map(extractCategoryIpa)
  }

  /* it has been implemented in this way
    in order to maintain backwards compatibility
    with the current exposed model which requires the IPA category
   */
  private def extractCategoryIpa(attributes: Seq[AttributeProcessApiAttribute]): String =
    attributes.find(_.origin == "IPA".some).map(_.name).getOrElse("Unknown")

  override def getJWKByKid(kid: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerKey: ToEntityMarshaller[JWK],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = authorize {
    val operationLabel = s"Retrieving JWK of key with kId: $kid"
    logger.info(operationLabel)

    val result: Future[JWK] = for {
      maybeKey <- readModel.findOne[JWK]("keys", Filters.eq("data.kid", kid))
      key      <- maybeKey.toFuture(KeyNotFound(kid))
    } yield key

    onComplete(result) {
      getKeyJWKfromKIdResponse[JWK](operationLabel)(getJWKByKid200)
    }
  }

  override def getEServices(limit: Int, offset: Int)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerCatalogEServices: ToEntityMarshaller[CatalogEServices]
  ): Route = {
    val operationLabel = "Retrieving EServices"
    logger.info(operationLabel)

    val result: Future[CatalogEServices] = for {
      pagedResults <- catalogProcessService.getEServices(
        producerIds = Seq.empty,
        attributeIds = Seq.empty,
        offset = offset,
        limit = limit
      )
    } yield CatalogEServices(
      results = pagedResults.results
        .map(eservice => CatalogEService(id = eservice.id, name = eservice.name, description = eservice.description)),
      pagination = Pagination(offset = offset, limit = limit, totalCount = pagedResults.totalCount)
    )

    onComplete(result) {
      getEservicesResponse[CatalogEServices](operationLabel)(getEServices200)
    }
  }
}
