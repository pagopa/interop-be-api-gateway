package it.pagopa.interop.apigateway.service.impl

import it.pagopa.interop.apigateway.service.{PurposeManagementService, PurposeManagementInvoker}
import it.pagopa.pdnd.interop.uservice.purposemanagement.client.api.PurposeApi
import it.pagopa.pdnd.interop.uservice.purposemanagement.client.invoker.BearerToken
import java.util.UUID
import cats.implicits._
import scala.concurrent.Future
import it.pagopa.pdnd.interop.uservice.purposemanagement.client.model.{Purpose, Purposes}
import it.pagopa.pdnd.interop.uservice.purposemanagement.client.invoker.ApiRequest
import org.slf4j.{Logger, LoggerFactory}
import it.pagopa.pdnd.interop.uservice.partymanagement.client.invoker.ApiError
import it.pagopa.pdnd.interop.commons.utils.errors.GenericComponentErrors

class PurposeManagementServiceImpl(invoker: PurposeManagementInvoker, api: PurposeApi)
    extends PurposeManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getPurpose(purposeId: UUID)(bearerToken: String): Future[Purpose] = {
    val request: ApiRequest[Purpose] = api.getPurpose(purposeId)(BearerToken(bearerToken))
    invoker.invoke(request, "Invoking getPurpose", handleCommonErrors(s"purpose $purposeId"))
  }

  override def getPurposes(eserviceId: UUID, consumerId: UUID)(bearerToken: String): Future[Purposes] = {
    val request: ApiRequest[Purposes] = api.getPurposes(eserviceId.some, consumerId.some, Nil)(BearerToken(bearerToken))
    invoker.invoke(
      request,
      "Invoking getPurposes",
      handleCommonErrors(s"purposes for eservice $eserviceId and consumer $consumerId")
    )
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
