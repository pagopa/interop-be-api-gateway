package it.pagopa.interop.apigateway.service.impl

import it.pagopa.interop.apigateway.service.{AgreementManagementInvoker, AgreementManagementService}
import it.pagopa.pdnd.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.api.AgreementApi
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.invoker.{ApiError, ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{Agreement, AgreementState}
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.Future

class AgreementManagementServiceImpl(invoker: AgreementManagementInvoker, api: AgreementApi)
    extends AgreementManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getAgreementById(agreementId: UUID)(bearerToken: String): Future[Agreement] = {
    val request: ApiRequest[Agreement] = api.getAgreement(agreementId.toString)(BearerToken(bearerToken))
    invoker.invoke(request, s"Retrieving agreement by id = $agreementId", handleCommonErrors(s"agreement $agreementId"))
  }

  override def getAgreements(
    producerId: Option[String] = None,
    consumerId: Option[String] = None,
    eserviceId: Option[String] = None,
    descriptorId: Option[String] = None,
    state: Option[AgreementState] = None
  )(bearerToken: String): Future[Seq[Agreement]] = {

    val request: ApiRequest[Seq[Agreement]] =
      api.getAgreements(
        producerId = producerId,
        consumerId = consumerId,
        eserviceId = eserviceId,
        descriptorId = descriptorId,
        state = state
      )(BearerToken(bearerToken))

    invoker.invoke(request, "Retrieving agreements")
  }

  private[service] def handleCommonErrors[T](
    resource: String
  ): (Logger, String) => PartialFunction[Throwable, Future[T]] = { (logger, msg) =>
    {
      case ex @ ApiError(code, message, _, _, _) if code == 404 =>
        logger.error(s"$msg. code > $code - message > $message", ex)
        Future.failed(GenericComponentErrors.ResourceNotFoundError(resource))
      case ex =>
        logger.error(s"$msg. Error: ${ex.getMessage}", ex)
        Future.failed(ex)
    }
  }

}
