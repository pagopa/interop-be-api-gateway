package it.pagopa.interop.apigateway.service.impl

import it.pagopa.interop.apigateway.service.{AttributeRegistryManagementInvoker, AttributeRegistryManagementService}
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.interop.attributeregistrymanagement.client.invoker.{ApiError, ApiRequest, BearerToken}
import it.pagopa.interop.attributeregistrymanagement.client.model.Attribute
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.Future

class AttributeRegistryManagementServiceImpl(invoker: AttributeRegistryManagementInvoker, api: AttributeApi)
    extends AttributeRegistryManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getAttributeById(attributeId: UUID)(bearerToken: String): Future[Attribute] = {
    val request: ApiRequest[Attribute] =
      api.getAttributeById(attributeId = attributeId)(BearerToken(bearerToken))
    invoker.invoke(
      request,
      s"Retrieving attribute by id ${attributeId.toString}",
      handleCommonErrors(s"attribute $attributeId")
    )
  }

  private[service] def handleCommonErrors[T](
    resource: String
  ): (Logger, String) => PartialFunction[Throwable, Future[T]] = { (logger, msg) =>
    {
      case ex @ ApiError(code, message, _, _, _) if code == 404 =>
        logger.error(s"$msg. code > $code - message > $message - ${ex.getMessage}")
        Future.failed(GenericComponentErrors.ResourceNotFoundError(resource))
      case ex                                                   =>
        logger.error(s"$msg. Error: ${ex.getMessage}")
        Future.failed(ex)
    }
  }
}
