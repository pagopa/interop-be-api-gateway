package it.pagopa.interop.apigateway.error

import it.pagopa.interop.commons.utils.errors.ComponentError

import java.util.UUID

object GatewayErrors {

  final case object Forbidden extends ComponentError("0001", s"The user has no access to the requested resource")
  final case object InternalServerError extends ComponentError("0002", "There was an internal server error")

  final case object ProducerAndConsumerParamMissing
      extends ComponentError("0003", "You should pass either producerId or consumerId")

  final case class EServiceDescriptorNotFound(eserviceId: String, descriptorId: String)
      extends ComponentError("0004", s"Descriptor $descriptorId not found for e-service $eserviceId")

  final case class MissingActivePurposeVersion(purposeId: UUID)
      extends ComponentError("0005", s"There is no active version for purpose $purposeId")

  final case class MissingActivePurposesVersions(purposesIds: Seq[UUID])
      extends ComponentError("0006", s"There is no active version for purposes ${purposesIds.mkString(", ")}")

  final case class UnexpectedDescriptorState(state: String)
      extends ComponentError("0007", s"Unexpected Descriptor state: $state")

  final case class MissingAvailableDescriptor(eServiceId: UUID)
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

  final case class OrganizationIsNotACertifier(organizationId: UUID)
      extends ComponentError("0014", s"Organization ${organizationId} is not a certifier")

  final case class TenantProcessBadRequest(resource: String) extends ComponentError("0015", s"Bad request - $resource")

  case object InvalidAgreementState extends ComponentError("0016", s"Cannot retrieve agreement in DRAFT state")
  case object MissingSelfcareId     extends ComponentError("0017", s"Tenant does not have a selfcareId")

  final case class AgreementNotFound(agreementId: String)
      extends ComponentError("0018", s"Agreement $agreementId not found")

  final case class InstitutionNotFound(origin: String, externalId: String)
      extends ComponentError("0019", s"Institution ($origin, $externalId) not found")

  final case class TenantAttributeNotFound(origin: String, externalId: String, attributeCode: String)
      extends ComponentError(
        "0020",
        s"Attribute $attributeCode not found for Institution ($origin, $externalId) not found"
      )

  final case class AttributeNotFound(attributeId: UUID)
      extends ComponentError("0021", s"Attribute $attributeId not found")

  final case class AttributeByOriginNotFound(origin: String, externalId: String)
      extends ComponentError("0022", s"Attribute ($origin, $externalId) not found")

  final case class EServiceNotFound(eServiceId: UUID) extends ComponentError("0023", s"EService $eServiceId not found")

  final case class TenantNotFound(tenantId: UUID) extends ComponentError("0024", s"Tenant $tenantId not found")

  final case class TenantByOriginNotFound(origin: String, externalId: String)
      extends ComponentError("0025", s"Tenant ($origin, $externalId) not found")

  final case class PurposeNotFound(purposeId: UUID) extends ComponentError("0026", s"Purpose $purposeId not found")

  final case class ActiveAgreementByEServiceAndConsumerNotFound(eServiceId: UUID, consumerId: UUID)
      extends ComponentError("0027", s"Active Agreement not found for EService $eServiceId and Consumer $consumerId")

  final case class MultipleAgreementForEServiceAndConsumer(eServiceId: UUID, consumerId: UUID)
      extends ComponentError(
        "0028",
        s"Unexpected multiple Active Agreements for EService $eServiceId and Consumer $consumerId"
      )

}
