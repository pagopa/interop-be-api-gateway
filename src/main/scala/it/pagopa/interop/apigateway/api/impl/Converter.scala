package it.pagopa.interop.apigateway.api.impl

import it.pagopa.interop.tenantprocess.client.model.{
  Tenant => M2MTenant,
  M2MTenantSeed,
  ExternalId => M2MExternalId,
  M2MAttributeSeed
}
import it.pagopa.interop.apigateway.model.{Tenant, TenantSeed, ExternalId, AttributeSeed}

object Converter {

  def m2mTenantSeedFromApi(tenantSeed: TenantSeed): M2MTenantSeed = M2MTenantSeed(
    externalId = m2mExternalIdFromApi(tenantSeed.externalId),
    certifiedAttributes = tenantSeed.certifiedAttributes.toList.map(m2mAttributeSeedFromApi)
  )

  private def m2mExternalIdFromApi(externalId: ExternalId): M2MExternalId             =
    M2MExternalId(externalId.origin, externalId.value)
  private def m2mAttributeSeedFromApi(attributeSeed: AttributeSeed): M2MAttributeSeed = M2MAttributeSeed(
    attributeSeed.code
  )

  def m2mTenantToApi(m2mTenant: M2MTenant): Tenant = Tenant(m2mTenant.id)

}
