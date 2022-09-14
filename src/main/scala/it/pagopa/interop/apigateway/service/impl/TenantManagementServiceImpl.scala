package it.pagopa.interop.apigateway.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.apigateway.service.{TenantManagementInvoker, TenantManagementService}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.interop.commons.utils.extractHeaders
import it.pagopa.interop.tenantmanagement.client.api.TenantApi
import it.pagopa.interop.tenantmanagement.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.tenantmanagement.client.model._

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
    result <- invoker.invoke(
      request,
      "Retrieve Tenant by external ID",
      handleCommonErrors(s"Origin $origin Code $code")
    )
  } yield result

  private[service] def handleCommonErrors[T](
    resource: String
  ): (ContextFieldsToLog, LoggerTakingImplicit[ContextFieldsToLog], String) => PartialFunction[Throwable, Future[T]] = {
    (contexts, logger, msg) =>
      {
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
