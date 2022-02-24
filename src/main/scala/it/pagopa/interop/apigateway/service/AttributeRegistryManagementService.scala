package it.pagopa.interop.apigateway.service

import it.pagopa.interop.attributeregistrymanagement.client.model.Attribute

import java.util.UUID
import scala.concurrent.Future

trait AttributeRegistryManagementService {

  def getAttributeById(attributeId: UUID)(bearerToken: String): Future[Attribute]

}
