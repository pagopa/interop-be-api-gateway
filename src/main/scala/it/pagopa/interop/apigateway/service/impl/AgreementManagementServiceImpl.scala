package it.pagopa.interop.apigateway.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementmanagement.client.api.AgreementApi
import it.pagopa.interop.agreementmanagement.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.agreementmanagement.client.model.{Agreement, AgreementState}
import it.pagopa.interop.apigateway.error.GatewayErrors.AgreementNotFound
import it.pagopa.interop.apigateway.service.{AgreementManagementInvoker, AgreementManagementService}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.extractHeaders

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class AgreementManagementServiceImpl(invoker: AgreementManagementInvoker, api: AgreementApi)(implicit
  ec: ExecutionContext
) extends AgreementManagementService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getAgreementById(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getAgreement(xCorrelationId = correlationId, agreementId.toString, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker
        .invoke(request, s"Retrieving agreement by id = $agreementId")
        .recoverWith {
          case err: ApiError[_] if err.code == 404 => Future.failed(AgreementNotFound(agreementId.toString))
        }
    } yield result
  }

  override def getAgreements(
    producerId: Option[String] = None,
    consumerId: Option[String] = None,
    eserviceId: Option[String] = None,
    descriptorId: Option[String] = None,
    states: List[AgreementState] = Nil
  )(implicit contexts: Seq[(String, String)]): Future[Seq[Agreement]] = {

    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getAgreements(
        xCorrelationId = correlationId,
        xForwardedFor = ip,
        producerId = producerId,
        consumerId = consumerId,
        eserviceId = eserviceId,
        descriptorId = descriptorId,
        states = states
      )(BearerToken(bearerToken))
      result <- invoker.invoke(request, "Retrieving agreements")
    } yield result

  }

}
