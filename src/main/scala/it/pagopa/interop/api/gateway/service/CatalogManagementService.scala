package it.pagopa.interop.api.gateway.service

import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.EService

import java.util.UUID
import scala.concurrent.Future

trait CatalogManagementService {

  def getEService(bearerToken: String, eServiceId: UUID): Future[EService]
}
