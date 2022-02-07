package it.pagopa.interop.api.gateway.service.impl

import it.pagopa.interop.api.gateway.service.{AuthorizationManagementInvoker, AuthorizationManagementService}
import it.pagopa.pdnd.interop.uservice.keymanagement.client.model.ClientKey

import java.util.UUID
import scala.concurrent.Future

class AuthorizationManagementServiceImpl(invoker: AuthorizationManagementInvoker)
    extends AuthorizationManagementService {
  override def getKey(clientId: UUID, kid: String)(bearer: String): Future[ClientKey] = ???
}
