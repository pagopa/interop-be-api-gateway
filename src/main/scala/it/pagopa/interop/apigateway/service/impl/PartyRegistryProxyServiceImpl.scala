package it.pagopa.interop.apigateway.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.apigateway.service.{PartyRegistryInvoker, PartyRegistryProxyService}
import it.pagopa.interop.commons.utils.extractHeaders
import it.pagopa.interop.partyregistryproxy.client.api.InstitutionApi
import it.pagopa.interop.partyregistryproxy.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.partyregistryproxy.client.model._
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors

import scala.concurrent.{ExecutionContext, Future}

class PartyRegistryProxyServiceImpl(invoker: PartyRegistryInvoker, api: InstitutionApi)(implicit ec: ExecutionContext)
    extends PartyRegistryProxyService {
  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getInstitutionByExternalId(origin: String, originId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Institution] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.getInstitutionByExternalId(
      // xCorrelationId = correlationId,
      origin = origin,
      originId = originId
      // xForwardedFor = ip
    )(BearerToken(bearerToken))
    result <- invoker.invoke(
      request,
      "Retrieve Institution by external ID",
      handleCommonErrors(s"Origin $origin Code $originId")
    )
  } yield result

  private[service] def handleCommonErrors[T](
    resource: String
  ): (ContextFieldsToLog, LoggerTakingImplicit[ContextFieldsToLog], String) => PartialFunction[Throwable, Future[T]] = {
    (contexts, logger, msg) =>
      {
        case ex @ ApiError(code, message, _, _, _) if code == 404 =>
          logger.warn(s"$msg. code > $code - message > $message - ${ex.getMessage}")(contexts)
          Future.failed(GenericComponentErrors.ResourceNotFoundError(resource))
        case ex                                                   =>
          logger.error(s"$msg. Error: ${ex.getMessage}")(contexts)
          Future.failed(ex)
      }
  }
}
