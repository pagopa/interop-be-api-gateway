package it.pagopa.interop.apigateway.service.impl

import it.pagopa.interop.apigateway.service.{AuthorizationManagementInvoker, AuthorizationManagementService}
import it.pagopa.interop.authorizationmanagement.client.api.ClientApi
import it.pagopa.interop.authorizationmanagement.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.authorizationmanagement.client.model.Client
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.interop.commons.utils.extractHeaders
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class AuthorizationManagementServiceImpl(invoker: AuthorizationManagementInvoker, api: ClientApi)(implicit
  ec: ExecutionContext
) extends AuthorizationManagementService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getClientById(clientId: UUID)(implicit contexts: Seq[(String, String)]): Future[Client] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getClient(xCorrelationId = correlationId, clientId = clientId, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(request, s"Retrieving client by id = $clientId", handleCommonErrors(s"client $clientId"))
    } yield result
  }

  private[service] def handleCommonErrors[T](
    resource: String
  ): (ContextFieldsToLog, LoggerTakingImplicit[ContextFieldsToLog], String) => PartialFunction[Throwable, Future[T]] = {
    (context, logger, msg) =>
      {
        case ex @ ApiError(code, message, _, _, _) if code == 404 =>
          logger.error(s"$msg. code > $code - message > $message - ${ex.getMessage}")(context)
          Future.failed(GenericComponentErrors.ResourceNotFoundError(resource))
        case ex                                                   =>
          logger.error(s"$msg. Error: ${ex.getMessage}")(context)
          Future.failed(ex)
      }
  }

}
