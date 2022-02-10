package it.pagopa.interop.api.gateway.error

import it.pagopa.pdnd.interop.commons.utils.errors.ComponentError

object GatewayErrors {
  final case object AgreementNotFound extends ComponentError("0001", "No agreement was found")
  final case object InvalidAgreementsInput
      extends ComponentError("0002", "You should pass either producerId or consumerId")
  final case object AgreementsError extends ComponentError("0003", "Errors while retrieving list of agreements")

  final case class AttributeNotFoundError(attributeId: String)
      extends ComponentError("0004", s"No attribute identified as $attributeId was found for this organization")

  final case object EServiceNotFoundForOrganizationError
      extends ComponentError("0005", "E-Service not found for this organization")

  final case object OrganizationError extends ComponentError("0006", "Error while retrieving organization")
  final case class EServiceDescriptorNotFound(eserviceId: String, descriptorId: String)
      extends ComponentError("0007", s"Descriptor $descriptorId not found for e-service $eserviceId")
}
