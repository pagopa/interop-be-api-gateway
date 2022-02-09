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

//object AgreementManagementService {
//  def agreementStateToApi(state: AgreementState): ApiAgreementState =
//    state match {
//      case AgreementState.ACTIVE    => ApiAgreementState.ACTIVE
//      case AgreementState.PENDING   => ApiAgreementState.PENDING
//      case AgreementState.SUSPENDED => ApiAgreementState.SUSPENDED
//      case AgreementState.INACTIVE  => ApiAgreementState.INACTIVE
//    }
//
//  def agreementToApi(agreement: Agreement, descriptor: ApiDescriptor): ApiAgreement =
//    ApiAgreement(id = agreement.id, state = agreementStateToApi(agreement.state), descriptor = descriptor)
//}
