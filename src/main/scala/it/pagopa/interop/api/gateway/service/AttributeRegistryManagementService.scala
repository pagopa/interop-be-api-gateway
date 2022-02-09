package it.pagopa.interop.api.gateway.service

import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.model.Attribute

import java.util.UUID
import scala.concurrent.Future

trait AttributeRegistryManagementService {

  def getAttributeById(attributeId: UUID)(bearerToken: String): Future[Attribute]

}