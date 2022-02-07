package it.pagopa.interop.api.gateway.service

import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{Agreement, AgreementState}
//import it.pagopa.interop.be.gateway.model.{Agreement => ApiAgreement, AgreementState => ApiAgreementState}

import java.util.UUID
import scala.concurrent.Future

trait AgreementManagementService {

  /** Returns the expected audience defined by the producer of the corresponding agreementId.
    *
    * @param agreementId
    * @return
    */

  def getAgreements(
    bearerToken: String,
    consumerId: UUID,
    eserviceId: UUID,
    status: Option[AgreementState]
  ): Future[Seq[Agreement]]
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
