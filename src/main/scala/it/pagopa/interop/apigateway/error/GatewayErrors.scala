package it.pagopa.interop.apigateway.error

import it.pagopa.pdnd.interop.commons.utils.errors.ComponentError

import java.util.UUID

object GatewayErrors {
  final case object InvalidAgreementsInput
      extends ComponentError("0001", "You should pass either producerId or consumerId")

  final case object AgreementsError extends ComponentError("0002", "Errors while retrieving list of agreements")

  final case class AttributeNotFoundError(attributeId: String)
      extends ComponentError("0003", s"No attribute identified as $attributeId was found for this organization")

  final case object OrganizationError extends ComponentError("0005", "Error while retrieving organization")

  final case class EServiceDescriptorNotFound(eserviceId: String, descriptorId: String)
      extends ComponentError("0006", s"Descriptor $descriptorId not found for e-service $eserviceId")

  final case class ClientNotActive(clientId: UUID) extends ComponentError("0007", s"Client $clientId is not active")

  final case class PurposeNotFound(clientId: UUID, purposeId: UUID)
      extends ComponentError("0008", s"Purpose $purposeId not found for client $clientId")

  final case class InactiveClient(clientId: UUID) extends ComponentError("0009", s"Client $clientId is inactive")

  final case object CreateTokenRequestError
      extends ComponentError("0010", "Error while creating a token for this request")

  final case class MissingActivePurposeVersion(purposeId: UUID)
      extends ComponentError("0011", s"There is no active version for purpose $purposeId")

  final case class MissingActivePurposesVersions(purposesIds: Seq[UUID])
      extends ComponentError("0012", s"There is no active version for purposes ${purposesIds.mkString(", ")}")

  final case object Forbidden extends ComponentError("9998", s"The user has no access to the requested resource")

  final case object InternalServerError extends ComponentError("9999", "There was an internal server error")

}
