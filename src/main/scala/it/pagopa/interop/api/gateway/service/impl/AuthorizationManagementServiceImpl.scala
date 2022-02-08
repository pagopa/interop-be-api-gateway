package it.pagopa.interop.api.gateway.service.impl

import it.pagopa.interop.api.gateway.service.{AuthorizationManagementInvoker, AuthorizationManagementService}
import it.pagopa.pdnd.interop.uservice.keymanagement.client.api.KeyApi
import it.pagopa.pdnd.interop.uservice.keymanagement.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.keymanagement.client.model._
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.Future

class AuthorizationManagementServiceImpl(invoker: AuthorizationManagementInvoker, keyApi: KeyApi)
    extends AuthorizationManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getKey(clientId: UUID, kid: String)(bearer: String): Future[ClientKey] = {
    val request: ApiRequest[ClientKey] = keyApi.getClientKeyById(clientId, kid)(BearerToken(bearer))
    invoker.invoke(request, "Key Retrieve")
  }
}
