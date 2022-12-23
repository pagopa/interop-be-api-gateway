package it.pagopa.interop.apigateway.service.impl

import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.apigateway.error.GatewayErrors.PurposeNotFound
import it.pagopa.interop.apigateway.service.{PurposeManagementInvoker, PurposeManagementService}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.extractHeaders
import it.pagopa.interop.purposemanagement.client.api.PurposeApi
import it.pagopa.interop.purposemanagement.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.purposemanagement.client.model.{Purpose, Purposes}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class PurposeManagementServiceImpl(invoker: PurposeManagementInvoker, api: PurposeApi)(implicit ec: ExecutionContext)
    extends PurposeManagementService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getPurpose(purposeId: UUID)(implicit contexts: Seq[(String, String)]): Future[Purpose] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.getPurpose(xCorrelationId = correlationId, purposeId, xForwardedFor = ip)(BearerToken(bearerToken))
    result <- invoker
      .invoke(request, "Invoking getPurpose")
      .recoverWith { case err: ApiError[_] if err.code == 404 => Future.failed(PurposeNotFound(purposeId)) }
  } yield result

  override def getPurposes(eserviceId: UUID, consumerId: UUID)(implicit
    contexts: Seq[(String, String)]
  ): Future[Purposes] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request = api.getPurposes(
      xCorrelationId = correlationId,
      xForwardedFor = ip,
      eserviceId.some,
      consumerId.some,
      Nil
    )(BearerToken(bearerToken))
    result <- invoker.invoke(request, "Invoking getPurposes")
  } yield result

}
