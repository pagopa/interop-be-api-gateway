package it.pagopa.interop.apigateway.service

import it.pagopa.interop.authorizationprocess.client.model.Client

import java.util.UUID
import scala.concurrent.Future

trait AuthorizationProcessService {

  def getClientById(clientId: UUID)(implicit contexts: Seq[(String, String)]): Future[Client]

}
