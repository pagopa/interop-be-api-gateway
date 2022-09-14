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

  final case class MissingAvailableDescriptor(eServiceId: String)
      extends ComponentError("0008", s"No available descriptors for EService $eServiceId")

  final case class AttributeNotFoundInRegistry(attributeId: UUID)
      extends ComponentError("0009", s"Attribute $attributeId not found in Attribute Registry")

  final case class MissingAttributeOrigin(attributeId: UUID)
      extends ComponentError("0010", s"Attribute $attributeId Origin is empty")

  final case class MissingAttributeCode(attributeId: UUID)
      extends ComponentError("0011", s"Attribute $attributeId Code is empty")

  final case class UnexpectedAttributeOrigin(attributeId: UUID, origin: String)
      extends ComponentError("0012", s"Attribute $attributeId has unexpected Origin $origin")

  final case class UnexpectedInstitutionOrigin(institutionId: UUID, origin: String)
      extends ComponentError("0013", s"Institution $institutionId has unexpected Origin $origin")

}
