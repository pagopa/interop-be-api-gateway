package it.pagopa.interop.apigateway.service

import it.pagopa.interop.attributeregistrymanagement.client.model.{Attribute, AttributeSeed, AttributesResponse}

import java.util.UUID
import scala.concurrent.Future

trait AttributeRegistryManagementService {

  def getAttributeById(attributeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Attribute]
  def getBulkAttributes(attributeIds: Set[UUID])(implicit contexts: Seq[(String, String)]): Future[AttributesResponse]

  def getAttributeByOriginAndCode(origin: String, code: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Attribute]

  def createAttribute(attributeSeed: AttributeSeed)(implicit contexts: Seq[(String, String)]): Future[Attribute]

}
