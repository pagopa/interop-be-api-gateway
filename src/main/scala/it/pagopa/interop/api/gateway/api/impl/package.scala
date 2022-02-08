package it.pagopa.interop.api.gateway.api

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCode
import it.pagopa.interop.be.gateway.model._
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{
  Agreement => AgreementManagementApiAgreement,
  AgreementState => AgreementManagementApiAgreementState
}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{EService => CatalogManagementApiEService}
import it.pagopa.pdnd.interop.uservice.partymanagement.client.model.{Organization => PartyManagementApiOrganization}
import it.pagopa.pdnd.interop.commons.utils.SprayCommonFormats.uuidFormat
import it.pagopa.pdnd.interop.commons.utils.errors.ComponentError
import spray.json.{DefaultJsonProtocol, RootJsonFormat}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.AgreementState.ACTIVE
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.AgreementState.INACTIVE
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.AgreementState.PENDING
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.AgreementState.SUSPENDED

package object impl extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val problemErrorFormat: RootJsonFormat[ProblemError] = jsonFormat2(ProblemError)
  implicit val problemFormat: RootJsonFormat[Problem]           = jsonFormat5(Problem)

  implicit val clientCredentialsResponseFormat: RootJsonFormat[ClientCredentialsResponse] = jsonFormat3(
    ClientCredentialsResponse
  )

  implicit val purposeFormat: RootJsonFormat[Purpose] = jsonFormat3(Purpose)

  implicit val subscriberFormat: RootJsonFormat[Organization] = jsonFormat3(Organization)
  implicit val eServiceFormat: RootJsonFormat[EService]       = jsonFormat2(EService)

  implicit val agreementFormat: RootJsonFormat[Agreement]   = jsonFormat7(Agreement)
  implicit val agreementsFormat: RootJsonFormat[Agreements] = jsonFormat1(Agreements)

  implicit val attributeFormat: RootJsonFormat[Attribute] = jsonFormat3(Attribute)

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
    def toModel(eservice: EService, producer: Organization, consumer: Organization): Agreement =
      Agreement(
        id = agreement.id,
        eservice = eservice,
        producer = producer,
        consumer = consumer,
        state = agreement.state.toModel,
        attributes = Seq.empty,
        purposes = None
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
  }

  implicit class EnrichedOrganization(private val organization: PartyManagementApiOrganization) extends AnyVal {
    def toModel: Organization =
      Organization(
        id = organization.id,
        name = organization.description,
        category = "categoriaIPA"
      ) //TODO! Capire con Stefano che é
  }

}
