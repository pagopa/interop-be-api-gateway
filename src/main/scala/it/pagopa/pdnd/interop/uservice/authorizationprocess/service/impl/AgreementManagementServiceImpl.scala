package it.pagopa.pdnd.interop.uservice.authorizationprocess.service.impl

import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.api.AgreementApi
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{Agreement, AgreementState}
import it.pagopa.pdnd.interop.uservice.authorizationprocess.service.{
  AgreementManagementInvoker,
  AgreementManagementService
}
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.Future

class AgreementManagementServiceImpl(invoker: AgreementManagementInvoker, api: AgreementApi)
    extends AgreementManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

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
