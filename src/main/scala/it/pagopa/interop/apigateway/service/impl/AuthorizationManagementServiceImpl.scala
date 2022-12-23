package it.pagopa.interop.apigateway.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.apigateway.error.GatewayErrors.ClientNotFound
import it.pagopa.interop.apigateway.service.{AuthorizationManagementInvoker, AuthorizationManagementService}
import it.pagopa.interop.authorizationmanagement.client.api.ClientApi
import it.pagopa.interop.authorizationmanagement.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.authorizationmanagement.client.model.Client
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.extractHeaders

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class AuthorizationManagementServiceImpl(invoker: AuthorizationManagementInvoker, api: ClientApi)(implicit
  ec: ExecutionContext
) extends AuthorizationManagementService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getClientById(clientId: UUID)(implicit contexts: Seq[(String, String)]): Future[Client] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.getClient(xCorrelationId = correlationId, clientId = clientId, xForwardedFor = ip)(
      BearerToken(bearerToken)
    )
    result <- invoker
      .invoke(request, s"Retrieving client by id = $clientId")
      .recoverWith { case err: ApiError[_] if err.code == 404 => Future.failed(ClientNotFound(clientId)) }
  } yield result

}
