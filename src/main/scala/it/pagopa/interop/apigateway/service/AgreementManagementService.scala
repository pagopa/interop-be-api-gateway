package it.pagopa.interop.apigateway.service

import it.pagopa.interop.agreementmanagement.client.model.{Agreement, AgreementState}
import it.pagopa.interop.apigateway.error.GatewayErrors
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait AgreementManagementService {

  def getAgreements(
    producerId: Option[String] = None,
    consumerId: Option[String] = None,
    eserviceId: Option[String] = None,
    descriptorId: Option[String] = None,
    state: Option[AgreementState] = None
  )(implicit contexts: Seq[(String, String)]): Future[Seq[Agreement]]

  def getAgreementById(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement]

  def getActiveOrSuspendedAgreementByConsumerAndEserviceId(consumerUUID: UUID, eserviceUUID: UUID)(implicit
    contexts: Seq[(String, String)],
    ec: ExecutionContext
  ): Future[Agreement] = {
    getAgreements(
      producerId = None,
      consumerId = Some(consumerUUID.toString),
      eserviceId = Some(eserviceUUID.toString),
      descriptorId = None,
      state = None
    )(contexts).flatMap(agreements =>
      agreements
        .filter(a => a.state == AgreementState.ACTIVE || a.state == AgreementState.SUSPENDED) match {
        case head :: Nil => Future.successful(head)
        case Nil         =>
          Future.failed(
            GenericComponentErrors
              .ResourceNotFoundError(s"agreement on (consumer, eservice): ($consumerUUID, $eserviceUUID)")
          )
        // This is the case that "should never happen" in whom the tuple (consumerUUID, eserviceUUID)
        // is no more exaustive in identifying a unique active/suspended agreement
        case _           => Future.failed(GatewayErrors.InternalServerError)
      }
    )
  }

}
