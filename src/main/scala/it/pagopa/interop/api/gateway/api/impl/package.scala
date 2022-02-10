package it.pagopa.interop.api.gateway.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCode
import cats.implicits._
import it.pagopa.interop.be.gateway.model._
import it.pagopa.pdnd.interop.commons.utils.SprayCommonFormats.uuidFormat
import it.pagopa.pdnd.interop.commons.utils.TypeConversions.StringOps
import it.pagopa.pdnd.interop.commons.utils.errors.ComponentError
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.AgreementState.{
  ACTIVE,
  INACTIVE,
  PENDING,
  SUSPENDED
}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{
  Agreement => AgreementManagementApiAgreement,
  AgreementState => AgreementManagementApiAgreementState
}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.model.{
  Attribute => AttributeRegistryManagementApiAttribute
}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{
  Attribute => CatalogManagementApiAttribute,
  EService => CatalogManagementApiEService
}
import it.pagopa.pdnd.interop.uservice.partymanagement.client.model.{Organization => PartyManagementApiOrganization}
import spray.json.{DefaultJsonProtocol, RootJsonFormat}

import java.util.UUID
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{
  VerifiedAttribute => AgreementManagementApiVerifiedAttribute
}
import scala.annotation.nowarn
import java.time.OffsetDateTime

package object impl extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val problemErrorFormat: RootJsonFormat[ProblemError] = jsonFormat2(ProblemError)
  implicit val problemFormat: RootJsonFormat[Problem]           = jsonFormat5(Problem)

  implicit val clientCredentialsResponseFormat: RootJsonFormat[ClientCredentialsResponse] = jsonFormat3(
    ClientCredentialsResponse
  )

  implicit val purposeFormat: RootJsonFormat[Purpose]   = jsonFormat3(Purpose)
  implicit val purposesFormat: RootJsonFormat[Purposes] = jsonFormat1(Purposes)

  implicit val subscriberFormat: RootJsonFormat[Organization] = jsonFormat3(Organization)
  implicit val eServiceFormat: RootJsonFormat[EService]       = jsonFormat4(EService)

  implicit val agreementFormat: RootJsonFormat[Agreement]   = jsonFormat6(Agreement)
  implicit val agreementsFormat: RootJsonFormat[Agreements] = jsonFormat1(Agreements)

  implicit val attributeFormat: RootJsonFormat[Attribute] = jsonFormat3(Attribute)

  implicit val attributeValidityStateFormat: RootJsonFormat[AttributeValidityState] = jsonFormat2(
    AttributeValidityState
  )

  implicit val attributesFormat: RootJsonFormat[Attributes] = jsonFormat1(Attributes)

  final val serviceErrorCodePrefix: String = "013"
  final val defaultProblemType: String     = "about:blank"

  def problemOf(httpError: StatusCode, error: ComponentError, defaultMessage: String = "Unknown error"): Problem =
    Problem(
      `type` = defaultProblemType,
      status = httpError.intValue,
      title = httpError.defaultMessage,
      errors = Seq(
        ProblemError(
          code = s"$serviceErrorCodePrefix-${error.code}",
          detail = Option(error.getMessage).getOrElse(defaultMessage)
        )
      )
    )

  implicit class EnrichedAgreement(private val agreement: AgreementManagementApiAgreement) extends AnyVal {
    def toModel: Agreement =
      Agreement(
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
      case ACTIVE    => AgreementState.ACTIVE
      case INACTIVE  => AgreementState.INACTIVE
      case PENDING   => AgreementState.PENDING
      case SUSPENDED => AgreementState.SUSPENDED
    }
  }

  implicit class EnrichedEService(private val eservice: CatalogManagementApiEService) extends AnyVal {
    def toModel: EService = EService(eservice.id, eservice.name)

    private def flatAttributes(attribute: CatalogManagementApiAttribute): Set[UUID] = {
      val flattenAttributes: Option[Seq[String]] = for {
        single <- attribute.single.map(_.id)
        group  <- attribute.group
      } yield group.map(_.id).appended(single)

      flattenAttributes
        .fold(List.empty[UUID]) { ids =>
          ids.toList.traverse(_.toUUID).getOrElse(List.empty)
        }
        .toSet
    }

    def isVerified(attribute: AgreementManagementApiVerifiedAttribute): Boolean =
      attribute.verified.contains(true)

    def isInTimeRange(attribute: AgreementManagementApiVerifiedAttribute): Boolean =
      attribute.verificationDate.zip(attribute.validityTimespan).fold(true) { case (verDate, offset) =>
        OffsetDateTime.now().isBefore(verDate.plusSeconds(offset))
      }

    def attributeUUIDSummary(
      @nowarn certifiedFromParty: Set[UUID], //TODO replace with the correct model once it's created
      verifiedFromAgreement: Set[AgreementManagementApiVerifiedAttribute],
      @nowarn declaredFromAgreement: Set[UUID] //TODO replace with the correct model once it's created
    ): Set[AttributeValidityState] = eservice.attributes.verified.toSet
      .flatMap(flatAttributes)
      .map(uuid =>
        verifiedFromAgreement
          .find(_.id == uuid)
          .fold(AttributeValidityState(uuid.toString, AttributeValidity.INVALID))(attr => {
            if (isVerified(attr) && isInTimeRange(attr)) {
              AttributeValidityState(uuid.toString, AttributeValidity.VALID)
            } else AttributeValidityState(uuid.toString, AttributeValidity.INVALID)
          })
      )

    def attributesUUIDs: Set[UUID] =
      (eservice.attributes.declared ++ eservice.attributes.certified ++ eservice.attributes.verified).toSet
        .flatMap(flatAttributes)
  }

  implicit class EnrichedOrganization(private val organization: PartyManagementApiOrganization) extends AnyVal {
    def toModel: Organization =
      Organization(
        id = organization.id,
        name = organization.description,
        category = organization.attributes.headOption
          .map(_.description)
          .getOrElse("UNKOWN") //TODO, hey Jude consider to make this retrieval better
      )
  }

  //FIXME attribute kind MUST be properly retrieved from ???
  //TODO validity
  implicit class EnrichedAttribute(private val attribute: AttributeRegistryManagementApiAttribute) extends AnyVal {
    def toModel: Attribute = Attribute(id = attribute.id, name = attribute.name, kind = AttributeKind.DECLARED)
  }

}
