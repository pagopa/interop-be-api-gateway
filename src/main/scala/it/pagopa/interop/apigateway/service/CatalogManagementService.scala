package it.pagopa.interop.apigateway.service

import it.pagopa.interop.catalogmanagement.client.model.EService

import java.util.UUID
import scala.concurrent.Future

trait CatalogManagementService {

  def getEService(eServiceId: UUID)(implicit contexts: Seq[(String, String)]): Future[EService]
}
