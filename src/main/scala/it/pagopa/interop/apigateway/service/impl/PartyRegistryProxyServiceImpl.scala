package it.pagopa.interop.apigateway.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.apigateway.error.GatewayErrors.InstitutionNotFound
import it.pagopa.interop.apigateway.service.{PartyRegistryInvoker, PartyRegistryProxyService}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.extractHeaders
import it.pagopa.interop.partyregistryproxy.client.api.{InstitutionApi, AooApi, UoApi}
import it.pagopa.interop.partyregistryproxy.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.partyregistryproxy.client.model._

import scala.concurrent.{ExecutionContext, Future}

class PartyRegistryProxyServiceImpl(
  invoker: PartyRegistryInvoker,
  institutionApi: InstitutionApi,
  aooApi: AooApi,
  uoApi: UoApi
)(implicit ec: ExecutionContext)
    extends PartyRegistryProxyService {
  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getInstitutionByExternalId(origin: String, originId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Institution] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = institutionApi.getInstitutionByExternalId(
      xCorrelationId = correlationId,
      origin = origin,
      originId = originId,
      xForwardedFor = ip
    )(BearerToken(bearerToken))
    result <- invoker
      .invoke(request, "Retrieve Institution by external ID")
      .recoverWith { case err: ApiError[_] if err.code == 404 => Future.failed(InstitutionNotFound(origin, originId)) }
  } yield result

  override def getAOOByExternalId(origin: String, originId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Institution] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = aooApi.getAOOByExternalId(
      xCorrelationId = correlationId,
      origin = origin,
      originId = originId,
      xForwardedFor = ip
    )(BearerToken(bearerToken))
    result <- invoker
      .invoke(request, "Retrieve AOO Institution by external ID")
      .recoverWith { case err: ApiError[_] if err.code == 404 => Future.failed(InstitutionNotFound(origin, originId)) }
  } yield result

  override def getUOByExternalId(origin: String, originId: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Institution] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = uoApi.getUOByExternalId(
      xCorrelationId = correlationId,
      origin = origin,
      originId = originId,
      xForwardedFor = ip
    )(BearerToken(bearerToken))
    result <- invoker
      .invoke(request, "Retrieve UO Institution by external ID")
      .recoverWith { case err: ApiError[_] if err.code == 404 => Future.failed(InstitutionNotFound(origin, originId)) }
  } yield result
}
