package it.pagopa.interop.apigateway.service

import it.pagopa.interop.catalogprocess.client.model.EService

import java.util.UUID
import scala.concurrent.Future

trait CatalogProcessService {

  def getEService(eServiceId: UUID)(implicit contexts: Seq[(String, String)]): Future[EService]
  def getEServices(producerId: UUID, attributeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Seq[EService]]
}
