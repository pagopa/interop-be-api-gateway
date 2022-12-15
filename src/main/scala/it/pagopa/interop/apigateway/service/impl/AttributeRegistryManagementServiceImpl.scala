package it.pagopa.interop.apigateway.service.impl

import cats.implicits.catsSyntaxOptionId
import cats.syntax.all._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.apigateway.error.GatewayErrors.{
  AttributeAlreadyExists,
  AttributeByOriginNotFound,
  AttributeNotFound
}
import it.pagopa.interop.apigateway.service.{AttributeRegistryManagementInvoker, AttributeRegistryManagementService}
import it.pagopa.interop.attributeregistrymanagement.client.api.AttributeApi
import it.pagopa.interop.attributeregistrymanagement.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.attributeregistrymanagement.client.model.{Attribute, AttributeSeed, AttributesResponse}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.extractHeaders

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
    result <- invoker
      .invoke(request, s"Retrieving attribute by id ${attributeId.toString}")
      .adaptError { case err: ApiError[_] if err.code == 404 => AttributeNotFound(attributeId) }
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
    result <- invoker
      .invoke(request, s"Getting attribute ($origin,$code)")
      .adaptError { case err: ApiError[_] if err.code == 404 => AttributeByOriginNotFound(origin, code) }
  } yield result

  override def createAttribute(
    attributeSeed: AttributeSeed
  )(implicit contexts: Seq[(String, String)]): Future[Attribute] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.createAttribute(xCorrelationId = correlationId, attributeSeed = attributeSeed, xForwardedFor = ip)(
      BearerToken(bearerToken)
    )
    result <- invoker
      .invoke(request, s"Creating ${attributeSeed.kind} attribute ${attributeSeed.name}")
      .adaptError {
        case err: ApiError[_] if err.code == 409 =>
          (attributeSeed.origin, attributeSeed.code) match {
            case (Some(origin), Some(code)) => AttributeAlreadyExists(origin, code)
            case _                          => err
          }

      }
  } yield result

  override def getBulkAttributes(
    attributeIds: Set[UUID]
  )(implicit contexts: Seq[(String, String)]): Future[AttributesResponse] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request          = api.getBulkedAttributes(
        xCorrelationId = correlationId,
        ids = attributeIds.mkString(",").some,
        xForwardedFor = ip
      )(BearerToken(bearerToken))
      attributesString = attributeIds.mkString("[", ",", "]")
      result <- invoker.invoke(request, s"Retrieving bulk attributes $attributesString")
    } yield result
  }

}
