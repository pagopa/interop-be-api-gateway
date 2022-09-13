package it.pagopa.interop.apigateway.service

import it.pagopa.interop.tenantprocess.client.model.{Tenant, M2MTenantSeed}
import scala.concurrent.Future
import java.util.UUID

trait TenantProcessService {
  def upsertTenant(m2MTenantSeed: M2MTenantSeed)(implicit contexts: Seq[(String, String)]): Future[Tenant]
  def getTenant(id: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant]
}
