package it.pagopa.interop.apigateway.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.apigateway.error.GatewayErrors.{TenantByOriginNotFound, TenantNotFound}
import it.pagopa.interop.apigateway.service.{TenantManagementInvoker, TenantManagementService}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.extractHeaders
import it.pagopa.interop.tenantmanagement.client.api.TenantApi
import it.pagopa.interop.tenantmanagement.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.tenantmanagement.client.model._

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class TenantManagementServiceImpl(invoker: TenantManagementInvoker, api: TenantApi)(implicit ec: ExecutionContext)
    extends TenantManagementService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getTenantByExternalId(origin: String, code: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Tenant] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.getTenantByExternalId(
      xCorrelationId = correlationId,
      origin = origin,
      code = code,
      xForwardedFor = ip
    )(BearerToken(bearerToken))
    result <- invoker
      .invoke(request, "Retrieve Tenant by external ID")
      .recoverWith { case err: ApiError[_] if err.code == 404 => Future.failed(TenantByOriginNotFound(origin, code)) }
  } yield result

  override def getTenantById(tenantId: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.getTenant(xCorrelationId = correlationId, tenantId = tenantId, xForwardedFor = ip)(
      BearerToken(bearerToken)
    )
    result <- invoker
      .invoke(request, "Retrieve Tenant by ID")
      .recoverWith { case err: ApiError[_] if err.code == 404 => Future.failed(TenantNotFound(tenantId)) }
  } yield result

}
