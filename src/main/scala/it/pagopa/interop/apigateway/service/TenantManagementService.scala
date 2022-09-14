package it.pagopa.interop.apigateway.service

import it.pagopa.interop.tenantmanagement.client.model.Tenant

import scala.concurrent.Future

trait TenantManagementService {
  def getTenantByExternalId(origin: String, code: String)(implicit contexts: Seq[(String, String)]): Future[Tenant]
}
