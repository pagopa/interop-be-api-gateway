package it.pagopa.interop.api.gateway.service.impl

import it.pagopa.interop.api.gateway.service.{AgreementManagementInvoker, AgreementManagementService}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.api.AgreementApi
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{Agreement, AgreementState}
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.Future

class AgreementManagementServiceImpl(invoker: AgreementManagementInvoker, api: AgreementApi)
    extends AgreementManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getAgreementById(agreementId: String)(bearerToken: String): Future[Agreement] = {
    val request: ApiRequest[Agreement] = api.getAgreement(agreementId)(BearerToken(bearerToken))
    invoker.invoke(request, s"Retrieving agreement by id = $agreementId")
  }
  override def getAgreements(
    bearerToken: String,
    consumerId: UUID,
    eserviceId: UUID,
    state: Option[AgreementState]
  ): Future[Seq[Agreement]] = {
    val request: ApiRequest[Seq[Agreement]] =
      api.getAgreements(consumerId = Some(consumerId.toString), eserviceId = Some(eserviceId.toString), state = state)(
        BearerToken(bearerToken)
      )
    invoker.invoke(request, s"Retrieving active agreements for ${consumerId.toString}")
  }
}
