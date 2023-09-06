package it.pagopa.interop.apigateway.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.apigateway.error.GatewayErrors.EServiceNotFound
import it.pagopa.interop.apigateway.service.{CatalogProcessInvoker, CatalogProcessService}
import it.pagopa.interop.catalogprocess.client.api.{ProcessApi => EServiceApi}
import it.pagopa.interop.catalogprocess.client.invoker.{ApiError, ApiRequest, BearerToken}
import it.pagopa.interop.catalogprocess.client.model.{AgreementState, EService, EServiceDescriptorState, EServices}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.extractHeaders

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class CatalogProcessServiceImpl(invoker: CatalogProcessInvoker, api: EServiceApi)(implicit ec: ExecutionContext)
    extends CatalogProcessService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getEServiceById(eServiceId: UUID)(implicit contexts: Seq[(String, String)]): Future[EService] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request: ApiRequest[EService] = api.getEServiceById(xCorrelationId = correlationId, eServiceId, xForwardedFor = ip)(
      BearerToken(bearerToken)
    )
    result <- invoker
      .invoke(request, "Retrieving E-Service")
      .recoverWith { case err: ApiError[_] if err.code == 404 => Future.failed(EServiceNotFound(eServiceId)) }
  } yield result

  override def getEServices(
    name: Option[String] = None,
    eServicesIds: Seq[UUID],
    producersIds: Seq[UUID],
    attributesIds: Seq[UUID],
    agreementStates: Seq[AgreementState],
    states: Seq[EServiceDescriptorState],
    offset: Int,
    limit: Int
  )(implicit contexts: Seq[(String, String)]): Future[EServices] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request: ApiRequest[EServices] = api.getEServices(
      xCorrelationId = correlationId,
      name = None,
      eservicesIds = Seq.empty,
      producersIds = producersIds,
      attributesIds = attributesIds,
      states = Seq.empty,
      agreementStates = Seq.empty,
      offset = offset,
      limit = limit,
      xForwardedFor = ip
    )(BearerToken(bearerToken))
    result <- invoker.invoke(request, "Retrieving E-Services")
  } yield result
}
