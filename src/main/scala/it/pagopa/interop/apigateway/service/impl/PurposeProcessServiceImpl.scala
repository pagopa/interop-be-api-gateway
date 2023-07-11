package it.pagopa.interop.apigateway.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.apigateway.error.GatewayErrors.PurposeNotFound
import it.pagopa.interop.apigateway.service.{PurposeProcessInvoker, PurposeProcessService}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.extractHeaders
import it.pagopa.interop.purposeprocess.client.api.PurposeApi
import it.pagopa.interop.purposeprocess.client.invoker.{ApiRequest, ApiError, BearerToken}
import it.pagopa.interop.purposeprocess.client.model.{Purpose, Purposes}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class PurposeProcessServiceImpl(invoker: PurposeProcessInvoker, api: PurposeApi)(implicit ec: ExecutionContext)
    extends PurposeProcessService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getPurpose(purposeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Purpose] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request: ApiRequest[Purpose] = api.getPurpose(xCorrelationId = correlationId, purposeId, xForwardedFor = ip)(
      BearerToken(bearerToken)
    )
    result <- invoker
      .invoke(request, "Invoking getPurpose")
      .recoverWith { case err: ApiError[_] if err.code == 404 => Future.failed(PurposeNotFound(purposeId)) }
  } yield result

  override def getPurposes(eserviceId: UUID, consumerId: UUID, offset: Int, limit: Int)(implicit
    contexts: Seq[(String, String)]
  ): Future[Purposes] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request: ApiRequest[Purposes] = api.getPurposes(
      xCorrelationId = correlationId,
      xForwardedFor = ip,
      producersIds = Seq.empty,
      consumersIds = Seq(consumerId),
      eservicesIds = Seq(eserviceId),
      states = Seq.empty,
      offset = offset,
      limit = limit
    )(BearerToken(bearerToken))
    result <- invoker.invoke(request, "Invoking getPurposes")
  } yield result

}
