package it.pagopa.interop.apigateway.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.agreementprocess.client.api.AgreementApi
import it.pagopa.interop.agreementprocess.client.invoker.{ApiRequest, ApiError, BearerToken}
import it.pagopa.interop.agreementprocess.client.model.{Agreement, AgreementState, Agreements}
import it.pagopa.interop.apigateway.error.GatewayErrors.AgreementNotFound
import it.pagopa.interop.apigateway.service.{AgreementProcessInvoker, AgreementProcessService}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils.extractHeaders

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class AgreementProcessServiceImpl(invoker: AgreementProcessInvoker, api: AgreementApi)(implicit ec: ExecutionContext)
    extends AgreementProcessService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getAgreementById(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request: ApiRequest[Agreement] = api.getAgreementById(
      xCorrelationId = correlationId,
      agreementId.toString,
      xForwardedFor = ip
    )(BearerToken(bearerToken))
    result <- invoker
      .invoke(request, s"Retrieving agreement by id = $agreementId")
      .recoverWith {
        case err: ApiError[_] if err.code == 404 => Future.failed(AgreementNotFound(agreementId.toString))
      }
  } yield result

  override def getAgreements(
    producerId: Option[UUID] = None,
    consumerId: Option[UUID] = None,
    eserviceId: Option[UUID] = None,
    descriptorId: Option[UUID] = None,
    states: List[AgreementState] = Nil,
    offset: Int,
    limit: Int
  )(implicit contexts: Seq[(String, String)]): Future[Agreements] = for {
    (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
    request: ApiRequest[Agreements] = api.getAgreements(
      xCorrelationId = correlationId,
      xForwardedFor = ip,
      producersIds = producerId.fold[Seq[UUID]](Seq.empty)(Seq(_)),
      consumersIds = consumerId.fold[Seq[UUID]](Seq.empty)(Seq(_)),
      eservicesIds = eserviceId.fold[Seq[UUID]](Seq.empty)(Seq(_)),
      descriptorsIds = descriptorId.fold[Seq[UUID]](Seq.empty)(Seq(_)),
      states = states,
      offset = offset,
      limit = limit
    )(BearerToken(bearerToken))
    result <- invoker.invoke(request, "Retrieving agreements")
  } yield result

}
