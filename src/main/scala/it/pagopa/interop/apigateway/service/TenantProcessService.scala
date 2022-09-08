package it.pagopa.interop.apigateway.service

import it.pagopa.interop.tenantprocess.client.model.{Tenant, M2MTenantSeed}
import scala.concurrent.Future

trait TenantProcessService {
  def upsertTenant(m2MTenantSeed: M2MTenantSeed)(implicit contexts: Seq[(String, String)]): Future[Tenant]
}
