package it.pagopa.interop.apigateway.service.impl

import it.pagopa.interop.apigateway.service.{NotifierInvoker, NotifierService}
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.interop.notifier.client.api.EventsApi
import it.pagopa.interop.notifier.client.invoker.BearerToken
import it.pagopa.interop.notifier.client.model.Messages
import it.pagopa.interop.partymanagement.client.invoker.ApiError
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContext, Future}

class NotifierServiceImpl(invoker: NotifierInvoker, api: EventsApi)(implicit ec: ExecutionContext)
    extends NotifierService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getEvents(lastEventId: String, limit: Int)(contexts: Seq[(String, String)]): Future[Messages] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeadersWithOptionalCorrelationIdF(contexts)
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
  ): (Logger, String) => PartialFunction[Throwable, Future[T]] = { (logger, msg) =>
    {
      case ex @ ApiError(code, message, _, _, _) if code == 404 =>
        logger.error(s"$msg. code > $code - message > $message - ${ex.getMessage}")
        Future.failed(GenericComponentErrors.ResourceNotFoundError(resource))
      case ex                                                   =>
        logger.error(s"$msg. Error: ${ex.getMessage}")
        Future.failed(ex)
    }
  }

}
