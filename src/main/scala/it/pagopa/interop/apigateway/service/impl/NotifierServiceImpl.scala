package it.pagopa.interop.apigateway.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.apigateway.service.{NotifierInvoker, NotifierService}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.interop.commons.utils.extractHeaders
import it.pagopa.interop.notifier.client.api.EventsApi
import it.pagopa.interop.notifier.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.notifier.client.model.Events

import scala.concurrent.{ExecutionContext, Future}
class NotifierServiceImpl(invoker: NotifierInvoker, api: EventsApi)(implicit ec: ExecutionContext)
    extends NotifierService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getEvents(lastEventId: Long, limit: Int)(implicit contexts: Seq[(String, String)]): Future[Events] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getEventsFromId(
        lastEventId,
        xCorrelationId = Some(correlationId),
        xForwardedFor = ip,
        limit = Some(limit)
      )(BearerToken(bearerToken))
      result <- invoker.invoke(
        request,
        "Retrieving message events",
        handleCommonErrors(s"Error while retrieving events from $lastEventId")
      )
    } yield result
  }

  private[service] def handleCommonErrors[T](
    resource: String
  ): (ContextFieldsToLog, LoggerTakingImplicit[ContextFieldsToLog], String) => PartialFunction[Throwable, Future[T]] =
    (context, logger, message) => {
      case ex @ ApiError(code, msg, _, _, _) if code == 404 =>
        logger.error(s"$message. code > $code - message > $msg - ${ex.getMessage}")(context)
        Future.failed(GenericComponentErrors.ResourceNotFoundError(resource))
      case ex                                               =>
        logger.error(s"$message. Error: ${ex.getMessage}")(context)
        Future.failed(ex)
    }

}
