package it.pagopa.interop.apigateway.service.impl

import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.apigateway.error.GatewayErrors.EServiceNotFound
import it.pagopa.interop.apigateway.service.{CatalogManagementInvoker, CatalogManagementService}
import it.pagopa.interop.catalogmanagement.client.api.EServiceApi
import it.pagopa.interop.catalogmanagement.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.catalogmanagement.client.model.EService
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.extractHeaders

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class CatalogManagementServiceImpl(invoker: CatalogManagementInvoker, api: EServiceApi)(implicit ec: ExecutionContext)
    extends CatalogManagementService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getEService(eServiceId: UUID)(implicit contexts: Seq[(String, String)]): Future[EService] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.getEService(xCorrelationId = correlationId, eServiceId.toString, xForwardedFor = ip)(
      BearerToken(bearerToken)
    )
    result <- invoker
      .invoke(request, "Retrieving E-Service")
      .recoverWith { case err: ApiError[_] if err.code == 404 => Future.failed(EServiceNotFound(eServiceId)) }
  } yield result

  override def getEServices(producerId: UUID, attributeId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Seq[EService]] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.getEServices(
      xCorrelationId = correlationId,
      producerId = producerId.toString.some,
      attributeId = attributeId.toString.some,
      xForwardedFor = ip
    )(BearerToken(bearerToken))
    result <- invoker.invoke(request, "Retrieving E-Services")
  } yield result

}
