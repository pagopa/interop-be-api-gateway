package it.pagopa.interop.apigateway.error

import it.pagopa.interop.commons.utils.errors.ComponentError

import java.util.UUID

object GatewayErrors {

  final case object Forbidden extends ComponentError("0001", s"The user has no access to the requested resource")
  final case object InternalServerError extends ComponentError("0002", "There was an internal server error")

  final case object InvalidAgreementsInput
      extends ComponentError("0003", "You should pass either producerId or consumerId")

  final case class EServiceDescriptorNotFound(eserviceId: String, descriptorId: String)
      extends ComponentError("0004", s"Descriptor $descriptorId not found for e-service $eserviceId")

  final case class MissingActivePurposeVersion(purposeId: UUID)
      extends ComponentError("0005", s"There is no active version for purpose $purposeId")

  final case class MissingActivePurposesVersions(purposesIds: Seq[UUID])
      extends ComponentError("0006", s"There is no active version for purposes ${purposesIds.mkString(", ")}")

  final case class UnexpectedDescriptorState(state: String)
      extends ComponentError("0007", s"Unexpected Descriptor state: $state")

}
