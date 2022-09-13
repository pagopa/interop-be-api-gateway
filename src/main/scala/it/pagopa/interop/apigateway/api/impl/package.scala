package it.pagopa.interop.apigateway.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCode
import cats.data.Validated
import cats.implicits._
import it.pagopa.interop.agreementmanagement.client.model.{
  Agreement => AgreementManagementApiAgreement,
  AgreementState => AgreementManagementApiAgreementState,
  VerifiedAttribute => AgreementManagementApiVerifiedAttribute
}
import it.pagopa.interop.apigateway.error.GatewayErrors.{
  MissingActivePurposeVersion,
  MissingActivePurposesVersions,
  UnexpectedDescriptorState
}
import it.pagopa.interop.apigateway.model._
import it.pagopa.interop.attributeregistrymanagement.client.model.{
  Attribute => AttributeRegistryManagementApiAttribute,
  AttributeKind => AttributeRegistryManagementApiAttributeKind
}
import it.pagopa.interop.authorizationmanagement.client.model.{Client => AuthorizationManagementApiClient}
import it.pagopa.interop.catalogmanagement.client.model.{
  AttributeValue,
  Attribute => CatalogManagementApiAttribute,
  EService => CatalogManagementApiEService,
  EServiceDoc => CatalogManagementApiEServiceDoc,
  EServiceDescriptor => CatalogManagementApiDescriptor,
  EServiceDescriptorState => CatalogManagementApiDescriptorState
}
import it.pagopa.interop.commons.utils.SprayCommonFormats.uuidFormat
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.ComponentError
import it.pagopa.interop.notifier.client.model.{Event => NotifierApiEvent, Events => NotifierApiEvents}
import it.pagopa.interop.selfcare.partymanagement.client.model.{Institution => PartyManagementApiInstitution}
import it.pagopa.interop.purposemanagement.client.model.{
  PurposeVersionState,
  Purpose => PurposeManagementApiPurpose,
  Purposes => PurposeManagementApiPurposes
}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import java.time.OffsetDateTime
import java.util.UUID
import scala.annotation.nowarn
import scala.util.{Failure, Success, Try}

package object impl extends SprayJsonSupport with DefaultJsonProtocol {

  implicit val problemErrorFormat: RootJsonFormat[ProblemError] = jsonFormat2(ProblemError)
  implicit val problemFormat: RootJsonFormat[Problem]           = jsonFormat5(Problem)

  implicit val purposeFormat: RootJsonFormat[Purpose]   = jsonFormat3(Purpose)
  implicit val purposesFormat: RootJsonFormat[Purposes] = jsonFormat1(Purposes)

  implicit val subscriberFormat: RootJsonFormat[Organization]               = jsonFormat3(Organization)
  implicit val eServiceDocFormat: RootJsonFormat[EServiceDoc]               = jsonFormat3(EServiceDoc)
  implicit val eServiceDescriptorFormat: RootJsonFormat[EServiceDescriptor] = jsonFormat10(EServiceDescriptor)

  implicit val agreementFormat: RootJsonFormat[Agreement]   = jsonFormat6(Agreement)
  implicit val agreementsFormat: RootJsonFormat[Agreements] = jsonFormat1(Agreements)

  implicit val attributeFormat: RootJsonFormat[Attribute] = jsonFormat3(Attribute)

  implicit val clientFormat: RootJsonFormat[Client] = jsonFormat2(Client)

  implicit val messageFormat: RootJsonFormat[Event]   = jsonFormat4(Event)
  implicit val messagesFormat: RootJsonFormat[Events] = jsonFormat2(Events)

  implicit val attributeValidityStateFormat: RootJsonFormat[AttributeValidityState] = jsonFormat2(
    AttributeValidityState
  )

  implicit val attributesFormat: RootJsonFormat[Attributes] = jsonFormat1(Attributes)

  final val entityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

  final val serviceErrorCodePrefix: String = "013"
  final val defaultProblemType: String     = "about:blank"
  final val defaultErrorMessage: String    = "Unknown error"

  def problemOf(httpError: StatusCode, error: ComponentError): Problem =
    Problem(
      `type` = defaultProblemType,
      status = httpError.intValue,
      title = httpError.defaultMessage,
      errors = Seq(
        ProblemError(
          code = s"$serviceErrorCodePrefix-${error.code}",
          detail = Option(error.getMessage).getOrElse(defaultErrorMessage)
        )
      )
    )

  def problemOf(httpError: StatusCode, errors: List[ComponentError]): Problem =
    Problem(
      `type` = defaultProblemType,
      status = httpError.intValue,
      title = httpError.defaultMessage,
      errors = errors.map(error =>
        ProblemError(
          code = s"$serviceErrorCodePrefix-${error.code}",
          detail = Option(error.getMessage).getOrElse(defaultErrorMessage)
        )
      )
    )

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
    def toModel: Agreement = Agreement(
      id = agreement.id,
      eserviceId = agreement.eserviceId,
      descriptorId = agreement.descriptorId,
      producerId = agreement.producerId,
      consumerId = agreement.consumerId,
      state = agreement.state.toModel
    )
  }

  implicit class EnrichedAgreementState(private val agreement: AgreementManagementApiAgreementState) extends AnyVal {
    def toModel: AgreementState = agreement match {
      case AgreementManagementApiAgreementState.ACTIVE    => AgreementState.ACTIVE
      case AgreementManagementApiAgreementState.INACTIVE  => AgreementState.INACTIVE
      case AgreementManagementApiAgreementState.PENDING   => AgreementState.PENDING
      case AgreementManagementApiAgreementState.SUSPENDED => AgreementState.SUSPENDED
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

  implicit class EnrichedEService(private val eservice: CatalogManagementApiEService) extends AnyVal {
    private def flatAttributes(attribute: CatalogManagementApiAttribute): Set[UUID] = {
      val allAttributes: Seq[Option[AttributeValue]] = attribute.group.sequence :+ attribute.single
      allAttributes.flatMap(attribute => attribute.map(_.id)).toSet
    }

    def isVerified(attribute: AgreementManagementApiVerifiedAttribute): Boolean = attribute.verified.contains(true)

    def isInTimeRange(attribute: AgreementManagementApiVerifiedAttribute): Boolean =
      attribute.verificationDate.zip(attribute.validityTimespan).fold(true) { case (verDate, offset) =>
        OffsetDateTime.now().isBefore(verDate.plusSeconds(offset))
      }

    def attributeUUIDSummary(
      @nowarn certifiedFromParty: Set[UUID],   // TODO replace with the correct model once it's created
      verifiedFromAgreement: Set[AgreementManagementApiVerifiedAttribute],
      @nowarn declaredFromAgreement: Set[UUID] // TODO replace with the correct model once it's created
    ): Set[AttributeValidityState] = eservice.attributes.verified.toSet
      .flatMap(flatAttributes)
      .map(uuid =>
        verifiedFromAgreement
          .find(_.id == uuid)
          .fold(AttributeValidityState(uuid, AttributeValidity.INVALID))(attr =>
            if (isVerified(attr) && isInTimeRange(attr)) {
              AttributeValidityState(uuid, AttributeValidity.VALID)
            } else AttributeValidityState(uuid, AttributeValidity.INVALID)
          )
      )

    def attributesUUIDs: Set[UUID] =
      (eservice.attributes.declared ++ eservice.attributes.certified ++ eservice.attributes.verified).toSet
        .flatMap(flatAttributes)
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
          state = state
        )
      )
  }

  implicit class EnrichedEServiceDoc(private val doc: CatalogManagementApiEServiceDoc) extends AnyVal {
    def toModel: EServiceDoc = EServiceDoc(id = doc.id, name = doc.name, contentType = doc.contentType)
  }

  implicit class EnrichedInstitution(private val institution: PartyManagementApiInstitution) extends AnyVal {
    def toModel: Organization =
      Organization(
        id = institution.id,
        name = institution.description,
        category = institution.attributes.headOption
          .map(_.description)
          .getOrElse("UNKNOWN") // TODO, hey Jude consider to make this retrieval better
      )
  }

  implicit class EnrichedAttribute(private val attribute: AttributeRegistryManagementApiAttribute) extends AnyVal {
    def toModel: Attribute = Attribute(id = attribute.id, name = attribute.name, kind = attribute.kind.toModel)
  }

  implicit class EnrichedAttributeKind(private val kind: AttributeRegistryManagementApiAttributeKind) extends AnyVal {
    def toModel: AttributeKind = kind match {
      case AttributeRegistryManagementApiAttributeKind.CERTIFIED => AttributeKind.CERTIFIED
      case AttributeRegistryManagementApiAttributeKind.DECLARED  => AttributeKind.DECLARED
      case AttributeRegistryManagementApiAttributeKind.VERIFIED  => AttributeKind.VERIFIED
    }
  }

  implicit class EnrichedClient(private val client: AuthorizationManagementApiClient) extends AnyVal {
    def toModel: Client = Client(id = client.id, consumerId = client.consumerId)
  }

  implicit class EnrichedEvent(private val events: NotifierApiEvents) extends AnyVal {
    def toModel: Try[Events] =
      Success(Events(lastEventId = events.lastEventId, events = events.events.map(toEventModel)))

    private[this] def toEventModel(event: NotifierApiEvent): Event = Event(
      eventId = event.eventId,
      eventType = event.eventType,
      objectType = event.objectType,
      objectId = event.objectId
    )
  }
}
