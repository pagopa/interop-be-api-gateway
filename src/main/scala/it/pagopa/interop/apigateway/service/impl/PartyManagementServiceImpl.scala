package it.pagopa.interop.apigateway.service.impl

import it.pagopa.interop.apigateway.service.{PartyManagementInvoker, PartyManagementService}
import it.pagopa.pdnd.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.pdnd.interop.uservice.partymanagement.client.api.PartyApi
import it.pagopa.pdnd.interop.uservice.partymanagement.client.invoker.{ApiError, ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.partymanagement.client.model._
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.Future

class PartyManagementServiceImpl(invoker: PartyManagementInvoker, api: PartyApi) extends PartyManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getOrganization(organizationId: UUID)(bearerToken: String): Future[Organization] = {
    val request: ApiRequest[Organization] = api.getOrganizationById(organizationId)(BearerToken(bearerToken))
    invoker.invoke(request, "Retrieve Organization", handleCommonErrors(s"organization $organizationId"))
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
