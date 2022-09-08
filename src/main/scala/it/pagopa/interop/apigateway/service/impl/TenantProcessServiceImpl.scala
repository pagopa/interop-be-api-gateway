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

class TenantProcessServiceImpl(invoker: TenantProcessInvoker, api: TenantApi)(implicit ec: ExecutionContext)
    extends TenantProcessService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def upsertTenant(m2MTenantSeed: M2MTenantSeed)(implicit contexts: Seq[(String, String)]): Future[Tenant] = {
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
  }

  private[service] def handleCommonErrors[T](
    resource: String
  ): (ContextFieldsToLog, LoggerTakingImplicit[ContextFieldsToLog], String) => PartialFunction[Throwable, Future[T]] = {
    (contexts, logger, msg) =>
      {
        case ex @ ApiError(code, message, _, _, _) if code == 404 =>
          logger.error(s"$msg. code > $code - message > $message - ${ex.getMessage}")(contexts)
          Future.failed(GenericComponentErrors.ResourceNotFoundError(resource))
        case ex @ ApiError(code, message, _, _, _) if code == 409 =>
          logger.error(s"$msg. code > $code - message > $message - ${ex.getMessage}")(contexts)
          Future.failed(GenericComponentErrors.OperationForbidden)
        case ex                                                   =>
          logger.error(s"$msg. Error: ${ex.getMessage}")(contexts)
          Future.failed(ex)
      }
  }
}
