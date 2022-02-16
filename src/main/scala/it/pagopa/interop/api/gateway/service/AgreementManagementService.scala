package it.pagopa.interop.api.gateway.service

import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{Agreement, AgreementState}
//import it.pagopa.interop.be.gateway.model.{Agreement => ApiAgreement, AgreementState => ApiAgreementState}

import scala.concurrent.Future
import java.util.UUID
import scala.concurrent.ExecutionContext
import it.pagopa.interop.api.gateway.error.GatewayErrors

trait AgreementManagementService {

  def getAgreements(
    producerId: Option[String] = None,
    consumerId: Option[String] = None,
    eserviceId: Option[String] = None,
    descriptorId: Option[String] = None,
    state: Option[AgreementState] = None
  )(bearerToken: String): Future[Seq[Agreement]]

  def getAgreementById(agreementId: UUID)(bearerToken: String): Future[Agreement]

  def getActiveOrSuspendedAgreementByConsumerAndEserviceId(consumerUUID: UUID, eserviceUUID: UUID)(
    bearerToken: String
  )(implicit ec: ExecutionContext): Future[Agreement] = {
    getAgreements(
      producerId = None,
      consumerId = Some(consumerUUID.toString),
      eserviceId = Some(eserviceUUID.toString),
      descriptorId = None,
      state = None
    )(bearerToken).flatMap(agreements =>
      agreements
        .filter(a => a.state == AgreementState.ACTIVE || a.state == AgreementState.SUSPENDED) match {
        case head :: Nil => Future.successful(head)
        case Nil         => Future.failed(GatewayErrors.AgreementNotFound)
        // This is the case that "should never happen" in whom the tuple (consumerUUID, eserviceUUID)
        // is no more exaustive in identifying a unique active/suspended agreement
        case _ => Future.failed(GatewayErrors.InternalServerError)
      }
    )
  }

}
