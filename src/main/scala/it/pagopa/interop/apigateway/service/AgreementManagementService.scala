package it.pagopa.interop.apigateway.service

import it.pagopa.interop.agreementmanagement.client.model.{Agreement, AgreementState}
import it.pagopa.interop.apigateway.error.GatewayErrors.{
  ActiveAgreementByEServiceAndConsumerNotFound,
  MultipleAgreementForEServiceAndConsumer
}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait AgreementManagementService {

  def getAgreements(
    producerId: Option[String] = None,
    consumerId: Option[String] = None,
    eserviceId: Option[String] = None,
    descriptorId: Option[String] = None,
    states: List[AgreementState] = Nil
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
      states = List(AgreementState.ACTIVE, AgreementState.SUSPENDED)
    )(contexts).flatMap {
      case head :: Nil => Future.successful(head)
      case Nil         => Future.failed(ActiveAgreementByEServiceAndConsumerNotFound(eserviceUUID, consumerUUID))
      // This is the case that "should never happen" in whom the tuple (consumerUUID, eserviceUUID)
      // is no more exaustive in identifying a unique active/suspended agreement
      case _           => Future.failed(MultipleAgreementForEServiceAndConsumer(eserviceUUID, consumerUUID))
    }

  }

}
