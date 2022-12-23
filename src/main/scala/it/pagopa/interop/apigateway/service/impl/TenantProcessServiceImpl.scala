package it.pagopa.interop.apigateway.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.apigateway.error.GatewayErrors
import it.pagopa.interop.apigateway.error.GatewayErrors.TenantAttributeNotFound
import it.pagopa.interop.apigateway.service.{TenantProcessInvoker, TenantProcessService}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.interop.commons.utils.extractHeaders
import it.pagopa.interop.tenantprocess.client.api.TenantApi
import it.pagopa.interop.tenantprocess.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.tenantprocess.client.model.{M2MTenantSeed, Tenant}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class TenantProcessServiceImpl(invoker: TenantProcessInvoker, api: TenantApi)(implicit ec: ExecutionContext)
    extends TenantProcessService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def upsertTenant(m2MTenantSeed: M2MTenantSeed)(implicit contexts: Seq[(String, String)]): Future[Tenant] =
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.m2mUpsertTenant(xCorrelationId = correlationId, m2MTenantSeed = m2MTenantSeed, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker
        .invoke(request, "Invoking m2mUpsertTenant")
        .recoverWith {
          case err: ApiError[_] if err.code == 400 =>
            Future.failed(
              GatewayErrors.TenantProcessBadRequest(
                s"Tenant (${m2MTenantSeed.externalId.origin}, ${m2MTenantSeed.externalId.value})"
              )
            )
          case err: ApiError[_] if err.code == 403 => Future.failed(GenericComponentErrors.OperationForbidden)
        }

    } yield result

  override def getTenant(id: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.getTenant(xCorrelationId = correlationId, id = id, xForwardedFor = ip)(BearerToken(bearerToken))
    result <- invoker.invoke(request, "Invoking getTenant")
  } yield result

  override def revokeAttribute(origin: String, externalId: String, code: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.m2mRevokeAttribute(
      xCorrelationId = correlationId,
      origin = origin,
      externalId = externalId,
      code = code,
      xForwardedFor = ip
    )(BearerToken(bearerToken))
    () <- invoker
      .invoke(request, "Invoking revokeAttribute")
      .recoverWith {
        case err: ApiError[_] if err.code == 400 =>
          Future.failed(GatewayErrors.TenantProcessBadRequest(s"Tenant ($origin, $externalId), Attribute $code"))
        case err: ApiError[_] if err.code == 403 => Future.failed(GenericComponentErrors.OperationForbidden)
        case err: ApiError[_] if err.code == 404 => Future.failed(TenantAttributeNotFound(origin, externalId, code))
      }
  } yield ()
}
