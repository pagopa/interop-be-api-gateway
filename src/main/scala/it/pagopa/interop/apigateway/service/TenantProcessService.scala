package it.pagopa.interop.apigateway.service

import it.pagopa.interop.tenantprocess.client.model.{Tenant, M2MTenantSeed}
import scala.concurrent.Future
import java.util.UUID

trait TenantProcessService {
  def getTenantByExternalId(origin: String, code: String)(implicit contexts: Seq[(String, String)]): Future[Tenant]
  def getTenantById(tenantId: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant]
  def upsertTenant(m2MTenantSeed: M2MTenantSeed)(implicit contexts: Seq[(String, String)]): Future[Tenant]
  def revokeAttribute(origin: String, externalId: String, code: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit]
}
