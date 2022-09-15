package it.pagopa.interop.apigateway.service.impl

import it.pagopa.interop.tenantprocess.client.api.TenantApi
import it.pagopa.interop.apigateway.service.{TenantProcessService, TenantProcessInvoker}
import com.typesafe.scalalogging.{LoggerTakingImplicit, Logger}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import scala.concurrent.{Future, ExecutionContext}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.interop.commons.utils.extractHeaders
import it.pagopa.interop.tenantprocess.client.model.{Tenant, M2MTenantSeed}
import it.pagopa.interop.tenantprocess.client.invoker.{BearerToken, ApiError}
import java.util.UUID
import it.pagopa.interop.apigateway.error.GatewayErrors

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
      result <- invoker.invoke(
        request,
        "Invoking m2mUpsertTenant",
        handleCommonErrors(s"m2MTenantSeed ${m2MTenantSeed.externalId}")
      )
    } yield result

  override def getTenant(id: UUID)(implicit contexts: Seq[(String, String)]): Future[Tenant] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.getTenant(xCorrelationId = correlationId, id = id, xForwardedFor = ip)(BearerToken(bearerToken))
    result <- invoker.invoke(request, "Invoking getTenant", handleCommonErrors(s"getTenant ${id.toString}"))
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
    () <- invoker.invoke(request, "Invoking revokeAttribute", handleCommonErrors(s"attribute $code"))
  } yield ()

  private[service] def handleCommonErrors[T](
    resource: String
  ): (ContextFieldsToLog, LoggerTakingImplicit[ContextFieldsToLog], String) => PartialFunction[Throwable, Future[T]] = {
    (contexts, logger, msg) =>
      {
        case ex @ ApiError(code, message, _, _, _) if code == 400 =>
          logger.error(s"$msg. code > $code - message > $message - ${ex.getMessage}")(contexts)
          Future.failed(GatewayErrors.TenantProcessBadRequest(resource))
        case ex @ ApiError(code, message, _, _, _) if code == 404 =>
          logger.error(s"$msg. code > $code - message > $message - ${ex.getMessage}")(contexts)
          Future.failed(GenericComponentErrors.ResourceNotFoundError(resource))
        case ex @ ApiError(code, message, _, _, _) if code == 403 =>
          logger.error(s"$msg. code > $code - message > $message - ${ex.getMessage}")(contexts)
          Future.failed(GenericComponentErrors.OperationForbidden)
        case ex                                                   =>
          logger.error(s"$msg. Error: ${ex.getMessage}")(contexts)
          Future.failed(ex)
      }
  }
}
