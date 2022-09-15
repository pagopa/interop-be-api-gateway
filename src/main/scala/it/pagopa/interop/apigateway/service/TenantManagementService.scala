package it.pagopa.interop.apigateway.service

import it.pagopa.interop.tenantmanagement.client.model.Tenant

import java.util.UUID
import scala.concurrent.Future

trait TenantManagementService {
  def getTenantByExternalId(origin: String, code: String)(implicit contexts: Seq[(String, String)]): Future[Tenant]
  def getTenantById(tenantId: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant]
}
