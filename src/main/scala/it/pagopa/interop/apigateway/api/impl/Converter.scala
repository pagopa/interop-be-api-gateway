package it.pagopa.interop.apigateway.api.impl

import it.pagopa.interop.tenantprocess.client.model.{
  Tenant => M2MTenant,
  M2MTenantSeed,
  ExternalId => M2MExternalId,
  M2MAttributeSeed
}
import it.pagopa.interop.apigateway.model.Tenant

object Converter {

  def m2mTenantSeedFromApi(origin: String, externalId: String)(code: String): M2MTenantSeed =
    M2MTenantSeed(M2MExternalId(origin, externalId), M2MAttributeSeed(code) :: Nil)

  def m2mTenantToApi(m2mTenant: M2MTenant): Tenant = Tenant(m2mTenant.id)

}
