package it.pagopa.interop.apigateway.service.impl

import it.pagopa.interop.apigateway.service.{PartyManagementInvoker, PartyManagementService}
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.interop.partymanagement.client.api.PartyApi
import it.pagopa.interop.partymanagement.client.invoker.{ApiError, ApiRequest, BearerToken}
import it.pagopa.interop.partymanagement.client.model._
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.Future

class PartyManagementServiceImpl(invoker: PartyManagementInvoker, api: PartyApi) extends PartyManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  override def getInstitution(institutionId: UUID)(bearerToken: String): Future[Institution] = {
    val request: ApiRequest[Institution] = api.getInstitutionById(institutionId)(BearerToken(bearerToken))
    invoker.invoke(request, "Retrieve Institution", handleCommonErrors(s"institution $institutionId"))
  }

  private[service] def handleCommonErrors[T](
    resource: String
  ): (Logger, String) => PartialFunction[Throwable, Future[T]] = { (logger, msg) =>
    {
      case ex @ ApiError(code, message, _, _, _) if code == 404 =>
        logger.error(s"$msg. code > $code - message > $message - ${ex.getMessage}")
        Future.failed(GenericComponentErrors.ResourceNotFoundError(resource))
      case ex                                                   =>
        logger.error(s"$msg. Error: ${ex.getMessage}")
        Future.failed(ex)
    }
  }

}
