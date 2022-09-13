package it.pagopa.interop.apigateway.service.impl

import it.pagopa.interop.apigateway.service.{AttributeRegistryManagementInvoker, AttributeRegistryManagementService}
import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.interop.attributeregistrymanagement.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.attributeregistrymanagement.client.model.{Attribute, AttributeSeed}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.interop.commons.utils.extractHeaders
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class AttributeRegistryManagementServiceImpl(invoker: AttributeRegistryManagementInvoker, api: AttributeApi)(implicit
  ec: ExecutionContext
) extends AttributeRegistryManagementService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getAttributeById(attributeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Attribute] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.getAttributeById(xCorrelationId = correlationId, attributeId = attributeId, xForwardedFor = ip)(
      BearerToken(bearerToken)
    )
    result <- invoker.invoke(
      request,
      s"Retrieving attribute by id ${attributeId.toString}",
      handleCommonErrors(s"attribute $attributeId")
    )
  } yield result

  def getAttributeByOriginAndCode(origin: String, code: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Attribute] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.getAttributeByOriginAndCode(
      xCorrelationId = correlationId,
      origin = origin,
      code = code,
      xForwardedFor = ip
    )(BearerToken(bearerToken))
    result <- invoker.invoke(
      request,
      s"Getting attribute ($origin,$code)",
      handleCommonErrors(s"attribute ($origin,$code)")
    )
  } yield result

  override def createAttribute(
    attributeSeed: AttributeSeed
  )(implicit contexts: Seq[(String, String)]): Future[Attribute] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.createAttribute(xCorrelationId = correlationId, attributeSeed = attributeSeed, xForwardedFor = ip)(
      BearerToken(bearerToken)
    )
    result <- invoker.invoke(
      request,
      s"Creating ${attributeSeed.kind} attribute ${attributeSeed.name}",
      handleCommonErrors(s"attribute ${attributeSeed.name}")
    )
  } yield result

  private[service] def handleCommonErrors[T](
    resource: String
  ): (ContextFieldsToLog, LoggerTakingImplicit[ContextFieldsToLog], String) => PartialFunction[Throwable, Future[T]] =
    (contexts, logger, msg) => {
      case ex @ ApiError(code, message, _, _, _) if code == 404 =>
        logger.error(s"$msg. code > $code - message > $message - ${ex.getMessage}")(contexts)
        Future.failed(GenericComponentErrors.ResourceNotFoundError(resource))
      case ex                                                   =>
        logger.error(s"$msg. Error: ${ex.getMessage}")(contexts)
        Future.failed(ex)
    }

}
