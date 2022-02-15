package it.pagopa.interop.api.gateway.service

import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{Agreement, AgreementState}
//import it.pagopa.interop.be.gateway.model.{Agreement => ApiAgreement, AgreementState => ApiAgreementState}

import scala.concurrent.Future

trait AgreementManagementService {

  def getAgreements(
    producerId: Option[String] = None,
    consumerId: Option[String] = None,
    eserviceId: Option[String] = None,
    descriptorId: Option[String] = None,
    state: Option[AgreementState] = None
  )(bearerToken: String): Future[Seq[Agreement]]

  def getAgreementById(agreementId: String)(bearerToken: String): Future[Agreement]

}
