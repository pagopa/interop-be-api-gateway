package it.pagopa.interop.api.gateway.service

import it.pagopa.pdnd.interop.uservice.keymanagement.client.model._

import java.util.UUID
import scala.concurrent.Future

trait AuthorizationManagementService {

  def getKey(clientId: UUID, kid: String)(bearer: String): Future[ClientKey]
  def getClient(clientId: UUID)(bearer: String): Future[Client]

}
