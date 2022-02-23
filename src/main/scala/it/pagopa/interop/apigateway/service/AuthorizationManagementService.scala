package it.pagopa.interop.apigateway.service

import it.pagopa.interop.authorizationmanagement.client.model._

import java.util.UUID
import scala.concurrent.Future

trait AuthorizationManagementService {

  def getKey(clientId: UUID, kid: String)(bearer: String): Future[ClientKey]
  def getClient(clientId: UUID)(bearer: String): Future[Client]

}
