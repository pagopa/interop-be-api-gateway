package it.pagopa.interop.apigateway.service

import it.pagopa.interop.attributeregistrymanagement.client.model.{Attribute, AttributesResponse}

import java.util.UUID
import scala.concurrent.Future

trait AttributeRegistryManagementService {

  def getAttributeById(attributeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Attribute]
  def getBulkAttributes(attributeIds: Set[UUID])(implicit contexts: Seq[(String, String)]): Future[AttributesResponse]

}
