package it.pagopa.interop.apigateway.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import cats.syntax.all._
import it.pagopa.interop.agreementprocess.client.model.{
  Agreement => AgreementProcessApiAgreement,
  AgreementState => AgreementProcessApiAgreementState
}
import it.pagopa.interop.apigateway.error.GatewayErrors
import it.pagopa.interop.apigateway.error.GatewayErrors._
import it.pagopa.interop.apigateway.model._
import it.pagopa.interop.attributeregistryprocess.client.model.{
  Attribute => AttributeRegistryProcessApiAttribute,
  AttributeKind => AttributeRegistryProcessApiAttributeKind
}
import it.pagopa.interop.authorizationprocess.client.model.{Client => AuthorizationProcessApiClient}
import it.pagopa.interop.catalogprocess.client.model.{
  Attribute => CatalogProcessApiAttribute,
  Attributes => CatalogProcessApiAttributes,
  EService => CatalogProcessApiEService,
  EServiceDescriptor => CatalogProcessApiDescriptor,
  EServiceDescriptorState => CatalogProcessApiDescriptorState,
  EServiceDoc => CatalogProcessApiEServiceDoc,
  EServiceTechnology => CatalogProcessApiTechnology
}
import it.pagopa.interop.commons.utils.SprayCommonFormats.uuidFormat
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.ComponentError
import it.pagopa.interop.notifier.client.model.{Event => NotifierApiEvent, Events => NotifierApiEvents}
import it.pagopa.interop.purposeprocess.client.model.{PurposeVersionState, Purpose => PurposeProcessApiPurpose}
import it.pagopa.interop.tenantprocess.client.model.{
  CertifiedTenantAttribute,
  DeclaredTenantAttribute,
  Tenant,
  VerifiedTenantAttribute
}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import scala.concurrent.Future
import java.util.UUID

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
  implicit val paginationFormat: RootJsonFormat[Pagination]                         = jsonFormat3(Pagination)
  implicit val catalogEServiceFormat: RootJsonFormat[CatalogEService]               = jsonFormat3(CatalogEService)
  implicit val catalogEServicesFormat: RootJsonFormat[CatalogEServices]             = jsonFormat2(CatalogEServices)
  implicit val agreementFormat: RootJsonFormat[Agreement]                           = jsonFormat6(Agreement)
  implicit val agreementsFormat: RootJsonFormat[Agreements]                         = jsonFormat1(Agreements)

  implicit val attributeFormat: RootJsonFormat[Attribute] = jsonFormat3(Attribute)

  implicit val clientFormat: RootJsonFormat[Client]                 = jsonFormat2(Client)
  implicit val otherPrimeInfoFormat: RootJsonFormat[OtherPrimeInfo] = jsonFormat3(OtherPrimeInfo)
  implicit val keyFormat: RootJsonFormat[JWK]                       = jsonFormat22(JWK)

  implicit val messageFormat: RootJsonFormat[Event]   = jsonFormat4(Event)
  implicit val messagesFormat: RootJsonFormat[Events] = jsonFormat2(Events)

  implicit val attributeValidityStateFormat: RootJsonFormat[AttributeValidityState] =
    jsonFormat2(AttributeValidityState)
  implicit val attributesFormat: RootJsonFormat[Attributes]                         = jsonFormat3(Attributes)

  final val entityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

  implicit class EnrichedPurpose(private val purpose: PurposeProcessApiPurpose) extends AnyVal {
    def toModel: Either[Throwable, Purpose] = {
      purpose.versions
        .sortBy(_.createdAt)
        .find(p => p.state == PurposeVersionState.ACTIVE || p.state == PurposeVersionState.SUSPENDED)
        .fold[Either[Throwable, Purpose]](Left(MissingActivePurposeVersion(purpose.id))) { v =>
          Right(Purpose(id = purpose.id, throughput = v.dailyCalls, state = v.state.toModel))
        }
    }
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

  implicit class EnrichedAgreement(private val agreement: AgreementProcessApiAgreement) extends AnyVal {
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

  implicit class EnrichedAgreementState(private val agreement: AgreementProcessApiAgreementState) extends AnyVal {
    def toModel: Either[Throwable, AgreementState] = agreement match {
      case AgreementProcessApiAgreementState.DRAFT     => GatewayErrors.InvalidAgreementState.asLeft[AgreementState]
      case AgreementProcessApiAgreementState.PENDING   => AgreementState.PENDING.asRight[Throwable]
      case AgreementProcessApiAgreementState.ACTIVE    => AgreementState.ACTIVE.asRight[Throwable]
      case AgreementProcessApiAgreementState.SUSPENDED => AgreementState.SUSPENDED.asRight[Throwable]
      case AgreementProcessApiAgreementState.ARCHIVED  => AgreementState.ARCHIVED.asRight[Throwable]
      case AgreementProcessApiAgreementState.MISSING_CERTIFIED_ATTRIBUTES =>
        AgreementState.MISSING_CERTIFIED_ATTRIBUTES.asRight[Throwable]
      case AgreementProcessApiAgreementState.REJECTED                     => AgreementState.REJECTED.asRight[Throwable]
    }
  }

  implicit class EnrichedEServiceDescriptorState(private val state: CatalogProcessApiDescriptorState) extends AnyVal {
    def toModel: Either[ComponentError, EServiceDescriptorState] = state match {
      case CatalogProcessApiDescriptorState.PUBLISHED  => Right(EServiceDescriptorState.PUBLISHED)
      case CatalogProcessApiDescriptorState.DEPRECATED => Right(EServiceDescriptorState.DEPRECATED)
      case CatalogProcessApiDescriptorState.SUSPENDED  => Right(EServiceDescriptorState.SUSPENDED)
      case CatalogProcessApiDescriptorState.ARCHIVED   => Right(EServiceDescriptorState.ARCHIVED)
      case CatalogProcessApiDescriptorState.DRAFT      =>
        Left(UnexpectedDescriptorState(CatalogProcessApiDescriptorState.DRAFT.toString))
    }
  }

  implicit class EnrichedEServiceTechnology(private val tech: CatalogProcessApiTechnology) extends AnyVal {
    def toModel: EServiceTechnology = tech match {
      case CatalogProcessApiTechnology.REST => EServiceTechnology.REST
      case CatalogProcessApiTechnology.SOAP => EServiceTechnology.SOAP
    }
  }

  implicit class EnrichedEService(private val eService: CatalogProcessApiEService) extends AnyVal {
    def latestAvailableDescriptor: Future[CatalogProcessApiDescriptor] =
      eService.descriptors
        .filter(_.state != CatalogProcessApiDescriptorState.DRAFT)
        .sortBy(_.version.toInt)
        .lastOption
        .toFuture(MissingAvailableDescriptor(eService.id))
  }

  implicit class EnrichedEServiceAttributeValue(private val attribute: CatalogProcessApiAttribute) extends AnyVal {
    def toModel(
      registryAttributes: Seq[AttributeRegistryProcessApiAttribute]
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

  implicit class EnrichedEServiceAttribute(
    private val attributes: Seq[Seq[it.pagopa.interop.apigateway.model.EServiceAttributeValue]]
  ) extends AnyVal {
    def toModel: Seq[EServiceAttribute] = {
      attributes.flatMap { list =>
        list match {
          case head :: Nil => Seq(EServiceAttribute(single = head.some, group = None))
          case values      => Seq(EServiceAttribute(single = None, group = values.some))
        }
      }
    }
  }

  implicit class EnrichedEServiceAttributes(private val attributes: CatalogProcessApiAttributes) extends AnyVal {
    def toModel(
      registryAttributes: Seq[AttributeRegistryProcessApiAttribute]
    ): Either[ComponentError, EServiceAttributes] = {
      for {
        certified <- attributes.certified.traverse(_.traverse(_.toModel(registryAttributes)))
        declared  <- attributes.declared.traverse(_.traverse(_.toModel(registryAttributes)))
        verified  <- attributes.verified.traverse(_.traverse(_.toModel(registryAttributes)))
      } yield EServiceAttributes(
        certified = certified.toModel,
        declared = declared.toModel,
        verified = verified.toModel
      )
    }

    def allIds: Set[UUID] = {
      (attributes.verified.flatten ++ attributes.declared.flatten ++ attributes.certified.flatten)
        .map(_.id)
        .toSet
    }
  }

  implicit class EnrichedEServiceDescriptor(private val descriptor: CatalogProcessApiDescriptor) extends AnyVal {
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

  implicit class EnrichedEServiceDoc(private val doc: CatalogProcessApiEServiceDoc) extends AnyVal {
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

  implicit class EnrichedAttribute(private val attribute: AttributeRegistryProcessApiAttribute)    extends AnyVal {
    def toModel: Attribute = Attribute(id = attribute.id, name = attribute.name, kind = attribute.kind.toModel)
  }
  implicit class EnrichedAttributeKind(private val kind: AttributeRegistryProcessApiAttributeKind) extends AnyVal {
    def toModel: AttributeKind = kind match {
      case AttributeRegistryProcessApiAttributeKind.CERTIFIED => AttributeKind.CERTIFIED
      case AttributeRegistryProcessApiAttributeKind.DECLARED  => AttributeKind.DECLARED
      case AttributeRegistryProcessApiAttributeKind.VERIFIED  => AttributeKind.VERIFIED
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

  implicit class EnrichedClient(private val client: AuthorizationProcessApiClient) extends AnyVal {
    def toModel: Client = Client(id = client.id, consumerId = client.consumerId)
  }

  implicit class EnrichedEvent(private val events: NotifierApiEvents) extends AnyVal {
    def toModel: Events = Events(lastEventId = events.lastEventId, events = events.events.map(toEventModel))

    private[this] def toEventModel(event: NotifierApiEvent): Event = Event(
      eventId = event.eventId,
      eventType = event.eventType,
      objectType = event.objectType.toString,
      objectId = event.objectId
    )
  }
}
