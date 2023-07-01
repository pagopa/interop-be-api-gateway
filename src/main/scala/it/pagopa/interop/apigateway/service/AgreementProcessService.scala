package it.pagopa.interop.apigateway.service

import it.pagopa.interop.agreementprocess.client.model.{Agreements, Agreement, AgreementState}
import it.pagopa.interop.apigateway.error.GatewayErrors.{
  ActiveAgreementByEServiceAndConsumerNotFound,
  MultipleAgreementForEServiceAndConsumer
}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait AgreementProcessService {

  def getAllAgreements(
    producerId: Option[UUID],
    consumerId: Option[UUID],
    eserviceId: Option[UUID],
    descriptorId: Option[UUID],
    states: List[AgreementState]
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Seq[Agreement]] = {

    def getAgreementsFrom(offset: Int): Future[Seq[Agreement]] =
      getAgreements(
        producerId = producerId,
        consumerId = consumerId,
        eserviceId = eserviceId,
        descriptorId = descriptorId,
        states = states,
        offset = offset,
        limit = 50
      ).map(_.results)

    def go(start: Int)(as: Seq[Agreement]): Future[Seq[Agreement]] =
      getAgreementsFrom(start).flatMap(agrs =>
        if (agrs.size < 50) Future.successful(as ++ agrs) else go(start + 50)(as ++ agrs)
      )

    go(0)(Nil)
  }

  def getAgreements(
    producerId: Option[UUID] = None,
    consumerId: Option[UUID] = None,
    eserviceId: Option[UUID] = None,
    descriptorId: Option[UUID] = None,
    states: List[AgreementState] = Nil,
    offset: Int,
    limit: Int
  )(implicit contexts: Seq[(String, String)]): Future[Agreements]

  def getAgreementById(agreementId: UUID)(implicit contexts: Seq[(String, String)]): Future[Agreement]

  def getActiveOrSuspendedAgreementByConsumerAndEserviceId(consumerUUID: UUID, eserviceUUID: UUID)(implicit
    contexts: Seq[(String, String)],
    ec: ExecutionContext
  ): Future[Agreement] = {
    getAllAgreements(
      producerId = None,
      consumerId = Some(consumerUUID),
      eserviceId = Some(eserviceUUID),
      descriptorId = None,
      states = List(AgreementState.ACTIVE, AgreementState.SUSPENDED)
    ).flatMap {
      case head :: Nil => Future.successful(head)
      case Nil         => Future.failed(ActiveAgreementByEServiceAndConsumerNotFound(eserviceUUID, consumerUUID))
      // This is the case that "should never happen" in whom the tuple (consumerUUID, eserviceUUID)
      // is no more exaustive in identifying a unique active/suspended agreement
      case _           => Future.failed(MultipleAgreementForEServiceAndConsumer(eserviceUUID, consumerUUID))
    }
  }

}
