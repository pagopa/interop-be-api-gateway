package it.pagopa.interop.apigateway.service.impl

import it.pagopa.interop.apigateway.service.{AttributeRegistryManagementInvoker, AttributeRegistryManagementService}
import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.interop.attributeregistrymanagement.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.attributeregistrymanagement.client.model.Attribute
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class AttributeRegistryManagementServiceImpl(invoker: AttributeRegistryManagementInvoker, api: AttributeApi)(implicit
  ec: ExecutionContext
) extends AttributeRegistryManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getAttributeById(attributeId: UUID)(contexts: Seq[(String, String)]): Future[Attribute] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeadersWithOptionalCorrelationIdF(contexts)
      request = api.getAttributeById(xCorrelationId = correlationId, attributeId = attributeId, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(
        request,
        s"Retrieving attribute by id ${attributeId.toString}",
        handleCommonErrors(s"attribute $attributeId")
      )
    } yield result
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
