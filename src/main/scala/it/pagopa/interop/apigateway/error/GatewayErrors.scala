package it.pagopa.interop.apigateway.error

import it.pagopa.interop.commons.utils.errors.ComponentError

import java.util.UUID

object GatewayErrors {

  final case object Forbidden extends ComponentError("0001", s"The user has no access to the requested resource")
  final case object InternalServerError extends ComponentError("0002", "There was an internal server error")

  final case object InvalidAgreementsInput
      extends ComponentError("0003", "You should pass either producerId or consumerId")

  final case object AgreementsError extends ComponentError("0004", "Errors while retrieving list of agreements")

  final case class AttributeNotFoundError(attributeId: String)
      extends ComponentError("0005", s"No attribute identified as $attributeId was found for this organization")

  final case object OrganizationError extends ComponentError("0006", "Error while retrieving organization")

  final case class EServiceDescriptorNotFound(eserviceId: String, descriptorId: String)
      extends ComponentError("0007", s"Descriptor $descriptorId not found for e-service $eserviceId")

  final case class ClientNotActive(clientId: UUID) extends ComponentError("0008", s"Client $clientId is not active")

  final case class PurposeNotFound(clientId: UUID, purposeId: UUID)
      extends ComponentError("0009", s"Purpose $purposeId not found for client $clientId")

  final case class InactiveClient(clientId: UUID, errorMessages: List[String])
      extends ComponentError("0010", s"Client $clientId is inactive: ${errorMessages.mkString(", ")}")

  final case class CreateTokenRequestError(error: String)
      extends ComponentError("0011", s"Error while creating a token for this request - $error")

  final case class MissingActivePurposeVersion(purposeId: UUID)
      extends ComponentError("0012", s"There is no active version for purpose $purposeId")

  final case class MissingActivePurposesVersions(purposesIds: Seq[UUID])
      extends ComponentError("0013", s"There is no active version for purposes ${purposesIds.mkString(", ")}")

  final case class InactivePurpose(state: String)   extends ComponentError("0014", s"Purpose is in state $state")
  final case class InactiveEservice(state: String)  extends ComponentError("0015", s"E-Service is in state $state")
  final case class InactiveAgreement(state: String) extends ComponentError("0016", s"Agreement is in state $state")

  final case object PurposeIdNotProvided
      extends ComponentError("0017", "purposeId claim does not exist in this assertion")

}
