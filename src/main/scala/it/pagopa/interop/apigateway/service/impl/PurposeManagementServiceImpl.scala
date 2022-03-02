package it.pagopa.interop.apigateway.service.impl

import cats.implicits._
import it.pagopa.interop.apigateway.service.{PurposeManagementInvoker, PurposeManagementService}
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.interop.partymanagement.client.invoker.ApiError
import it.pagopa.interop.purposemanagement.client.api.PurposeApi
import it.pagopa.interop.purposemanagement.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.interop.purposemanagement.client.model.{Purpose, Purposes}
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.Future

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
        logger.error(s"$msg. code > $code - message > $message - ${ex.getMessage}")
        Future.failed(GenericComponentErrors.ResourceNotFoundError(resource))
      case ex =>
        logger.error(s"$msg. Error: ${ex.getMessage}")
        Future.failed(ex)
    }
  }

}
