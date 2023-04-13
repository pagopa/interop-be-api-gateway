package it.pagopa.interop.apigateway.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import cats.data.Validated
import cats.implicits._
import it.pagopa.interop.agreementmanagement.client.model.{
  Agreement => AgreementManagementApiAgreement,
  AgreementState => AgreementManagementApiAgreementState
}
import it.pagopa.interop.apigateway.error.GatewayErrors
import it.pagopa.interop.apigateway.error.GatewayErrors._
import it.pagopa.interop.apigateway.model._
import it.pagopa.interop.attributeregistrymanagement.client.model.{
  Attribute => AttributeRegistryManagementApiAttribute,
  AttributeKind => AttributeRegistryManagementApiAttributeKind
}
import it.pagopa.interop.authorizationmanagement.client.model.{Client => AuthorizationManagementApiClient}
import it.pagopa.interop.catalogmanagement.client.model.{
  Attribute => CatalogManagementApiAttribute,
  AttributeValue => CatalogManagementApiAttributeValue,
  Attributes => CatalogManagementApiAttributes,
  EService => CatalogManagementApiEService,
  EServiceDescriptor => CatalogManagementApiDescriptor,
  EServiceDescriptorState => CatalogManagementApiDescriptorState,
  EServiceDoc => CatalogManagementApiEServiceDoc,
  EServiceTechnology => CatalogManagementApiTechnology
}
import it.pagopa.interop.commons.utils.SprayCommonFormats.uuidFormat
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.ComponentError
import it.pagopa.interop.notifier.client.model.{Event => NotifierApiEvent, Events => NotifierApiEvents}
import it.pagopa.interop.purposemanagement.client.model.{
  PurposeVersionState,
  Purpose => PurposeManagementApiPurpose,
  Purposes => PurposeManagementApiPurposes
}
import it.pagopa.interop.tenantmanagement.client.model.{
  CertifiedTenantAttribute,
  DeclaredTenantAttribute,
  VerifiedTenantAttribute,
  Tenant
}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import java.util.UUID
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val problemErrorFormat: RootJsonFormat[ProblemError] = jsonFormat2(ProblemError)
  implicit val problemFormat: RootJsonFormat[Problem]           = jsonFormat6(Problem)

  implicit val attributeSeedFormat: RootJsonFormat[AttributeSeed] = jsonFormat3(AttributeSeed)

  implicit val purposeFormat: RootJsonFormat[Purpose]   = jsonFormat3(Purpose)
  implicit val purposesFormat: RootJsonFormat[Purposes] = jsonFormat1(Purposes)

  implicit val externalIdFormat: RootJsonFormat[ExternalId]                   = jsonFormat2(ExternalId)
  implicit val organizationFormat: RootJsonFormat[Organization]               = jsonFormat4(Organization)
  implicit val eServiceDocFormat: RootJsonFormat[EServiceDoc]                 = jsonFormat3(EServiceDoc)
  implicit val eServiceDescriptorFormat: RootJsonFormat[EServiceDescriptor]   = jsonFormat11(EServiceDescriptor)
  implicit val eServiceDescriptorsFormat: RootJsonFormat[EServiceDescriptors] = jsonFormat1(EServiceDescriptors)

  implicit val eServiceAttributeValueFormat: RootJsonFormat[EServiceAttributeValue] =
    jsonFormat4(EServiceAttributeValue)
  implicit val eServiceAttributeFormat: RootJsonFormat[EServiceAttribute]           = jsonFormat2(EServiceAttribute)
  implicit val eServiceAttributesFormat: RootJsonFormat[EServiceAttributes]         = jsonFormat3(EServiceAttributes)
  implicit val eServiceFormat: RootJsonFormat[EService]                             = jsonFormat9(EService)
  implicit val eServicesFormat: RootJsonFormat[EServices]                           = jsonFormat1(EServices)

  implicit val agreementFormat: RootJsonFormat[Agreement]   = jsonFormat6(Agreement)
  implicit val agreementsFormat: RootJsonFormat[Agreements] = jsonFormat1(Agreements)

  implicit val attributeFormat: RootJsonFormat[Attribute] = jsonFormat3(Attribute)

  implicit val clientFormat: RootJsonFormat[Client] = jsonFormat2(Client)

  implicit val messageFormat: RootJsonFormat[Event]   = jsonFormat4(Event)
  implicit val messagesFormat: RootJsonFormat[Events] = jsonFormat2(Events)

  implicit val attributeValidityStateFormat: RootJsonFormat[AttributeValidityState] =
    jsonFormat2(AttributeValidityState)
  implicit val attributesFormat: RootJsonFormat[Attributes]                         = jsonFormat3(Attributes)

  implicit val otherPrimeInfoFormat: RootJsonFormat[OtherPrimeInfo] = jsonFormat3(OtherPrimeInfo)
  implicit val jwkFormat: RootJsonFormat[JWK]                       = jsonFormat22(JWK)

  final val entityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

  implicit class EnrichedPurpose(private val purpose: PurposeManagementApiPurpose) extends AnyVal {
    def toModel: Try[Purpose] =
      purpose.versions
        .sortBy(_.createdAt)
        .find(p => p.state == PurposeVersionState.ACTIVE || p.state == PurposeVersionState.SUSPENDED)
        .toTry(MissingActivePurposeVersion(purpose.id))
        .map(version => Purpose(id = purpose.id, throughput = version.dailyCalls, state = version.state.toModel))
  }

  implicit class EnrichedPurposeVersionState(private val state: PurposeVersionState) extends AnyVal {
    def toModel: PurposeState = state match {
      case PurposeVersionState.ACTIVE               => PurposeState.ACTIVE
      case PurposeVersionState.DRAFT                => PurposeState.DRAFT
      case PurposeVersionState.ARCHIVED             => PurposeState.ARCHIVED
      case PurposeVersionState.WAITING_FOR_APPROVAL => PurposeState.WAITING_FOR_APPROVAL
      case PurposeVersionState.SUSPENDED            => PurposeState.SUSPENDED
    }
  }

  implicit class EnrichedPurposes(private val purposes: PurposeManagementApiPurposes) extends AnyVal {
    def toModel: Try[Purposes] = purposes.purposes.toList.traverse(_.toModel.toValidated.toValidatedNel) match {
      case Validated.Valid(purposes) => Success(Purposes(purposes))
      case Validated.Invalid(errors) =>
        Failure(MissingActivePurposesVersions(errors.toList.collect { case MissingActivePurposeVersion(purposeId) =>
          purposeId
        }))
    }
  }

  implicit class EnrichedAgreement(private val agreement: AgreementManagementApiAgreement) extends AnyVal {
    def toModel: Either[Throwable, Agreement] =
      agreement.state.toModel.map(state =>
        Agreement(
          id = agreement.id,
          eserviceId = agreement.eserviceId,
          descriptorId = agreement.descriptorId,
          producerId = agreement.producerId,
          consumerId = agreement.consumerId,
          state = state
        )
      )
  }

  implicit class EnrichedAgreementState(private val agreement: AgreementManagementApiAgreementState) extends AnyVal {
    def toModel: Either[Throwable, AgreementState] = agreement match {
      case AgreementManagementApiAgreementState.DRAFT     => GatewayErrors.InvalidAgreementState.asLeft[AgreementState]
      case AgreementManagementApiAgreementState.PENDING   => AgreementState.PENDING.asRight[Throwable]
      case AgreementManagementApiAgreementState.ACTIVE    => AgreementState.ACTIVE.asRight[Throwable]
      case AgreementManagementApiAgreementState.SUSPENDED => AgreementState.SUSPENDED.asRight[Throwable]
      case AgreementManagementApiAgreementState.ARCHIVED  => AgreementState.ARCHIVED.asRight[Throwable]
      case AgreementManagementApiAgreementState.MISSING_CERTIFIED_ATTRIBUTES =>
        AgreementState.MISSING_CERTIFIED_ATTRIBUTES.asRight[Throwable]
      case AgreementManagementApiAgreementState.REJECTED => AgreementState.REJECTED.asRight[Throwable]
    }
  }

  implicit class EnrichedEServiceDescriptorState(private val state: CatalogManagementApiDescriptorState)
      extends AnyVal {
    def toModel: Either[ComponentError, EServiceDescriptorState] = state match {
      case CatalogManagementApiDescriptorState.PUBLISHED  => Right(EServiceDescriptorState.PUBLISHED)
      case CatalogManagementApiDescriptorState.DEPRECATED => Right(EServiceDescriptorState.DEPRECATED)
      case CatalogManagementApiDescriptorState.SUSPENDED  => Right(EServiceDescriptorState.SUSPENDED)
      case CatalogManagementApiDescriptorState.ARCHIVED   => Right(EServiceDescriptorState.ARCHIVED)
      case CatalogManagementApiDescriptorState.DRAFT      =>
        Left(UnexpectedDescriptorState(CatalogManagementApiDescriptorState.DRAFT.toString))
    }
  }

  implicit class EnrichedEServiceTechnology(private val tech: CatalogManagementApiTechnology) extends AnyVal {
    def toModel: EServiceTechnology = tech match {
      case CatalogManagementApiTechnology.REST => EServiceTechnology.REST
      case CatalogManagementApiTechnology.SOAP => EServiceTechnology.SOAP
    }
  }

  implicit class EnrichedEService(private val eService: CatalogManagementApiEService) extends AnyVal {
    def latestAvailableDescriptor: Future[CatalogManagementApiDescriptor] =
      eService.descriptors
        .filter(_.state != CatalogManagementApiDescriptorState.DRAFT)
        .sortBy(_.version.toInt)
        .lastOption
        .toFuture(MissingAvailableDescriptor(eService.id))
  }

  implicit class EnrichedEServiceAttributeValue(private val attribute: CatalogManagementApiAttributeValue)
      extends AnyVal {
    def toModel(
      registryAttributes: Seq[AttributeRegistryManagementApiAttribute]
    ): Either[ComponentError, EServiceAttributeValue] = for {
      regAttribute <- registryAttributes.find(_.id == attribute.id).toRight(AttributeNotFoundInRegistry(attribute.id))
      origin       <- regAttribute.origin.toRight(MissingAttributeOrigin(attribute.id))
      code         <- regAttribute.code.toRight(MissingAttributeCode(attribute.id))
    } yield EServiceAttributeValue(
      id = attribute.id,
      code = code,
      origin = origin,
      explicitAttributeVerification = attribute.explicitAttributeVerification
    )
  }

  implicit class EnrichedEServiceAttribute(private val attribute: CatalogManagementApiAttribute) extends AnyVal {
    def toModel(
      registryAttributes: Seq[AttributeRegistryManagementApiAttribute]
    ): Either[ComponentError, EServiceAttribute] =
      for {
        single <- attribute.single.traverse(_.toModel(registryAttributes))
        group  <- attribute.group.traverse(_.traverse(_.toModel(registryAttributes)))
      } yield EServiceAttribute(single = single, group = group)
  }

  implicit class EnrichedEServiceAttributes(private val attributes: CatalogManagementApiAttributes) extends AnyVal {
    def toModel(
      registryAttributes: Seq[AttributeRegistryManagementApiAttribute]
    ): Either[ComponentError, EServiceAttributes] = {
      for {
        certified <- attributes.certified.traverse(_.toModel(registryAttributes))
        declared  <- attributes.declared.traverse(_.toModel(registryAttributes))
        verified  <- attributes.verified.traverse(_.toModel(registryAttributes))
      } yield EServiceAttributes(certified = certified, declared = declared, verified = verified)
    }

    def allIds: Set[UUID] = {
      (attributes.verified ++ attributes.declared ++ attributes.certified)
        .mapFilter(a =>
          (a.single, a.group) match {
            case (Some(s), Some(g)) => Some(s :: g.toList)
            case (Some(s), None)    => Some(s :: Nil)
            case (None, g)          => g
          }
        )
        .flatten
        .map(_.id)
        .toSet
    }
  }

  implicit class EnrichedEServiceDescriptor(private val descriptor: CatalogManagementApiDescriptor) extends AnyVal {
    def toModel: Either[ComponentError, EServiceDescriptor] =
      descriptor.state.toModel.map(state =>
        EServiceDescriptor(
          id = descriptor.id,
          version = descriptor.version,
          description = descriptor.description,
          audience = descriptor.audience,
          voucherLifespan = descriptor.voucherLifespan,
          dailyCallsPerConsumer = descriptor.dailyCallsPerConsumer,
          dailyCallsTotal = descriptor.dailyCallsTotal,
          interface = descriptor.interface.map(_.toModel),
          docs = descriptor.docs.map(_.toModel),
          state = state,
          serverUrls = descriptor.serverUrls
        )
      )
  }

  implicit class EnrichedEServiceDoc(private val doc: CatalogManagementApiEServiceDoc) extends AnyVal {
    def toModel: EServiceDoc = EServiceDoc(id = doc.id, name = doc.name, contentType = doc.contentType)
  }

  implicit class EnrichedTenant(private val tenant: Tenant) extends AnyVal {
    def toModel(category: String): Organization =
      Organization(
        id = tenant.id,
        externalId = ExternalId(origin = tenant.externalId.origin, id = tenant.externalId.value),
        name = tenant.name,
        category = category
      )
  }

  implicit class EnrichedAttribute(private val attribute: AttributeRegistryManagementApiAttribute)    extends AnyVal {
    def toModel: Attribute = Attribute(id = attribute.id, name = attribute.name, kind = attribute.kind.toModel)
  }
  implicit class EnrichedAttributeKind(private val kind: AttributeRegistryManagementApiAttributeKind) extends AnyVal {
    def toModel: AttributeKind = kind match {
      case AttributeRegistryManagementApiAttributeKind.CERTIFIED => AttributeKind.CERTIFIED
      case AttributeRegistryManagementApiAttributeKind.DECLARED  => AttributeKind.DECLARED
      case AttributeRegistryManagementApiAttributeKind.VERIFIED  => AttributeKind.VERIFIED
    }
  }

  implicit class EnrichedVerifiedTenantAttribute(private val attribute: VerifiedTenantAttribute) extends AnyVal {
    def toAgreementModel: AttributeValidityState = AttributeValidityState(
      id = attribute.id,
      validity = if (attribute.verifiedBy.isEmpty) AttributeValidity.INVALID else AttributeValidity.VALID
    )
  }

  implicit class EnrichedCertifiedTenantAttribute(private val attribute: CertifiedTenantAttribute) extends AnyVal {
    def toAgreementModel: AttributeValidityState = AttributeValidityState(
      id = attribute.id,
      validity =
        attribute.revocationTimestamp.fold[AttributeValidity](AttributeValidity.VALID)(_ => AttributeValidity.INVALID)
    )
  }

  implicit class EnrichedDeclaredTenantAttribute(private val attribute: DeclaredTenantAttribute) extends AnyVal {
    def toAgreementModel: AttributeValidityState = AttributeValidityState(
      id = attribute.id,
      validity =
        attribute.revocationTimestamp.fold[AttributeValidity](AttributeValidity.VALID)(_ => AttributeValidity.INVALID)
    )
  }

  implicit class EnrichedClient(private val client: AuthorizationManagementApiClient) extends AnyVal {
    def toModel: Client = Client(id = client.id, consumerId = client.consumerId)
  }

  implicit class EnrichedEvent(private val events: NotifierApiEvents) extends AnyVal {
    def toModel: Events = Events(lastEventId = events.lastEventId, events = events.events.map(toEventModel))

    private[this] def toEventModel(event: NotifierApiEvent): Event = Event(
      eventId = event.eventId,
      eventType = event.eventType,
      objectType = event.objectType,
      objectId = event.objectId
    )
  }
}
