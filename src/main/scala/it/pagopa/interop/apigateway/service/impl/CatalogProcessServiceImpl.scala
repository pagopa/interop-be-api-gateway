package it.pagopa.interop.apigateway.service.impl

import cats.syntax.all._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.apigateway.error.GatewayErrors.EServiceNotFound
import it.pagopa.interop.apigateway.service.{CatalogProcessInvoker, CatalogProcessService}
import it.pagopa.interop.catalogprocess.client.api.{ProcessApi => EServiceApi}
import it.pagopa.interop.catalogprocess.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.catalogprocess.client.model.EService
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.extractHeaders

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class CatalogProcessServiceImpl(invoker: CatalogProcessInvoker, api: EServiceApi)(implicit ec: ExecutionContext)
    extends CatalogProcessService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getEService(eServiceId: UUID)(implicit contexts: Seq[(String, String)]): Future[EService] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.getEServiceById(xCorrelationId = correlationId, eServiceId.toString, xForwardedFor = ip)(
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
