package it.pagopa.interop.api.gateway.service.impl

import it.pagopa.interop.api.gateway.service.{AttributeRegistryManagementInvoker, AttributeRegistryManagementService}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.attributeregistrymanagement.client.model.Attribute
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.Future

class AttributeRegistryManagementServiceImpl(invoker: AttributeRegistryManagementInvoker, api: AttributeApi)
    extends AttributeRegistryManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getAttributeById(attributeId: UUID)(bearerToken: String): Future[Attribute] = {
    val request: ApiRequest[Attribute] =
      api.getAttributeById(attributeId = attributeId)(BearerToken(bearerToken))
    invoker.invoke(request, s"Retrieving attribute by id ${attributeId.toString}")
  }
}
