package it.pagopa.interop.apigateway.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.apigateway.service.{NotifierInvoker, NotifierService}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import it.pagopa.interop.commons.utils.extractHeaders
import it.pagopa.interop.notifier.client.api.EventsApi
import it.pagopa.interop.notifier.client.invoker.BearerToken
import it.pagopa.interop.notifier.client.model.Events

import scala.concurrent.{ExecutionContext, Future}
class NotifierServiceImpl(invoker: NotifierInvoker, api: EventsApi)(implicit ec: ExecutionContext)
    extends NotifierService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getEvents(lastEventId: Long, limit: Int)(implicit contexts: Seq[(String, String)]): Future[Events] =
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getEventsFromId(
        lastEventId,
        xCorrelationId = Some(correlationId),
        xForwardedFor = ip,
        limit = Some(limit)
      )(BearerToken(bearerToken))
      result <- invoker.invoke(request, "Retrieving message events")
    } yield result

  override def getAllOrganizationEvents(lastEventId: Long, limit: Int)(implicit
    contexts: Seq[(String, String)]
  ): Future[Events] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.getAllEventsFromId(
      lastEventId,
      xCorrelationId = Some(correlationId),
      xForwardedFor = ip,
      limit = Some(limit)
    )(BearerToken(bearerToken))
    result <- invoker.invoke(request, "Retrieving message events for all organizations")
  } yield result

  override def getKeysEvents(lastEventId: Long, limit: Int)(implicit contexts: Seq[(String, String)]): Future[Events] =
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getKeysEvents(
        lastEventId,
        xCorrelationId = Some(correlationId),
        xForwardedFor = ip,
        limit = Some(limit)
      )(BearerToken(bearerToken))
      result <- invoker.invoke(request, "Retrieving keys events")
    } yield result
}
