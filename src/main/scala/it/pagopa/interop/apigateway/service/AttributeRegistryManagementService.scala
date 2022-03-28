package it.pagopa.interop.apigateway.service

import it.pagopa.interop.attributeregistrymanagement.client.model.Attribute

import java.util.UUID
import scala.concurrent.Future

trait AttributeRegistryManagementService {

  def getAttributeById(attributeId: UUID)(contexts: Seq[(String, String)]): Future[Attribute]

}
