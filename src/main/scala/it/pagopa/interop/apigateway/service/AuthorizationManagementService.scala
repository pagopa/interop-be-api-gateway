package it.pagopa.interop.apigateway.service

import it.pagopa.interop.authorizationmanagement.client.model._

import java.util.UUID
import scala.concurrent.Future

trait AuthorizationManagementService {

  def getKey(clientId: UUID, kid: String)(contexts: Seq[(String, String)]): Future[ClientKey]
  def getClient(clientId: UUID)(contexts: Seq[(String, String)]): Future[Client]

}
