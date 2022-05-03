package it.pagopa.interop.apigateway.service

import it.pagopa.interop.authorizationmanagement.client.model.Client

import java.util.UUID
import scala.concurrent.Future

trait AuthorizationManagementService {

  def getClientById(clientId: UUID)(contexts: Seq[(String, String)]): Future[Client]

}
