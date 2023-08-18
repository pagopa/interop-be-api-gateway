package it.pagopa.interop.apigateway.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.apigateway.error.GatewayErrors.{
  AttributeAlreadyExists,
  AttributeByOriginNotFound,
  AttributeNotFound
}
import it.pagopa.interop.apigateway.service.{AttributeRegistryProcessInvoker, AttributeRegistryProcessService}
import it.pagopa.interop.attributeregistryprocess.client.api.AttributeApi
import it.pagopa.interop.attributeregistryprocess.client.invoker.{ApiRequest, ApiError, BearerToken}
import it.pagopa.interop.attributeregistryprocess.client.model.{Attributes, Attribute, CertifiedAttributeSeed}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.extractHeaders

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class AttributeRegistryProcessServiceImpl(invoker: AttributeRegistryProcessInvoker, api: AttributeApi)(implicit
  ec: ExecutionContext
) extends AttributeRegistryProcessService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getAttributeById(attributeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Attribute] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request: ApiRequest[Attribute] = api.getAttributeById(
      xCorrelationId = correlationId,
      attributeId = attributeId,
      xForwardedFor = ip
    )(BearerToken(bearerToken))
    result <- invoker
      .invoke(request, s"Retrieving attribute by id ${attributeId.toString}")
      .recoverWith { case err: ApiError[_] if err.code == 404 => Future.failed(AttributeNotFound(attributeId)) }
  } yield result

  def getAttributeByOriginAndCode(origin: String, code: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Attribute] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request: ApiRequest[Attribute] = api.getAttributeByOriginAndCode(
      xCorrelationId = correlationId,
      origin = origin,
      code = code,
      xForwardedFor = ip
    )(BearerToken(bearerToken))
    result <- invoker
      .invoke(request, s"Getting attribute ($origin,$code)")
      .recoverWith {
        case err: ApiError[_] if err.code == 404 => Future.failed(AttributeByOriginNotFound(origin, code))
      }
  } yield result

  override def createCertifiedAttribute(
    attributeSeed: CertifiedAttributeSeed
  )(implicit contexts: Seq[(String, String)]): Future[Attribute] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request: ApiRequest[Attribute] = api.createCertifiedAttribute(
      xCorrelationId = correlationId,
      certifiedAttributeSeed = attributeSeed,
      xForwardedFor = ip
    )(BearerToken(bearerToken))
    result <- invoker
      .invoke(request, s"Creating certified attribute ${attributeSeed.name}")
      .recoverWith {
        case err: ApiError[_] if err.code == 409 =>
          Future.failed(AttributeAlreadyExists(attributeSeed.name, attributeSeed.code))
      }
  } yield result

  override def getBulkAttributes(attributeIds: Set[UUID], offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)]
  ): Future[Attributes] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request: ApiRequest[Attributes] = api.getBulkedAttributes(
      xCorrelationId = correlationId,
      requestBody = attributeIds.map(_.toString).toSeq,
      offset = offset,
      limit = limit,
      xForwardedFor = ip
    )(BearerToken(bearerToken))
    attributesString                = attributeIds.mkString("[", ",", "]")
    result <- invoker.invoke(request, s"Retrieving bulk attributes $attributesString")
  } yield result

}
