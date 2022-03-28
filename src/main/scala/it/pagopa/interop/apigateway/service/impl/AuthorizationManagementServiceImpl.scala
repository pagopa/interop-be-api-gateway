package it.pagopa.interop.apigateway.service.impl

import it.pagopa.interop.apigateway.service.{AuthorizationManagementInvoker, AuthorizationManagementService}
import it.pagopa.interop.authorizationmanagement.client.api.{ClientApi, KeyApi}
import it.pagopa.interop.authorizationmanagement.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.authorizationmanagement.client.model._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.interop.commons.utils.extractHeaders
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class AuthorizationManagementServiceImpl(invoker: AuthorizationManagementInvoker, keyApi: KeyApi, clientApi: ClientApi)(
  implicit ec: ExecutionContext
) extends AuthorizationManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getKey(clientId: UUID, kid: String)(contexts: Seq[(String, String)]): Future[ClientKey] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = keyApi.getClientKeyById(xCorrelationId = correlationId, clientId, kid, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(request, "Key Retrieve", handleCommonErrors(s"clientKey $kid for client $clientId"))
    } yield result
  }

  override def getClient(clientId: UUID)(contexts: Seq[(String, String)]): Future[Client] = {

    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = clientApi.getClient(xCorrelationId = correlationId, clientId, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(request, "Client retrieval", handleCommonErrors(s"client $clientId"))
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
