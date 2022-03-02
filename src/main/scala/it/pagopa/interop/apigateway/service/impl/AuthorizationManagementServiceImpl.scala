package it.pagopa.interop.apigateway.service.impl

import it.pagopa.interop.apigateway.service.{AuthorizationManagementInvoker, AuthorizationManagementService}
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.interop.authorizationmanagement.client.api.{ClientApi, KeyApi}
import it.pagopa.interop.authorizationmanagement.client.invoker.{ApiError, ApiRequest, BearerToken}
import it.pagopa.interop.authorizationmanagement.client.model._
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.Future

class AuthorizationManagementServiceImpl(invoker: AuthorizationManagementInvoker, keyApi: KeyApi, clientApi: ClientApi)
    extends AuthorizationManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getKey(clientId: UUID, kid: String)(bearer: String): Future[ClientKey] = {
    val request: ApiRequest[ClientKey] = keyApi.getClientKeyById(clientId, kid)(BearerToken(bearer))
    invoker.invoke(request, "Key Retrieve", handleCommonErrors(s"clientKey $kid for client $clientId"))
  }

  override def getClient(clientId: UUID)(bearer: String): Future[Client] = {
    val request: ApiRequest[Client] = clientApi.getClient(clientId)(BearerToken(bearer))
    invoker.invoke(request, "Client retrieval", handleCommonErrors(s"client $clientId"))
  }

  private[service] def handleCommonErrors[T](
    resource: String
  ): (Logger, String) => PartialFunction[Throwable, Future[T]] = { (logger, msg) =>
    {
      case ex @ ApiError(code, message, _, _, _) if code == 404 =>
        logger.error(s"$msg. code > $code - message > $message - ${ex.getMessage}")
        Future.failed(GenericComponentErrors.ResourceNotFoundError(resource))
      case ex =>
        logger.error(s"$msg. Error: ${ex.getMessage}")
        Future.failed(ex)
    }
  }
}
