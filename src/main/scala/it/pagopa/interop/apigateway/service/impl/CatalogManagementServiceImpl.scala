package it.pagopa.interop.apigateway.service.impl

import it.pagopa.interop.apigateway.service.{CatalogManagementInvoker, CatalogManagementService}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.api.EServiceApi
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.invoker.{ApiRequest, BearerToken}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.EService
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.Future
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.invoker.ApiError
import it.pagopa.pdnd.interop.commons.utils.errors.GenericComponentErrors

class CatalogManagementServiceImpl(invoker: CatalogManagementInvoker, api: EServiceApi)
    extends CatalogManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  /** Returns the expected audience defined by the producer of the corresponding agreementId.
    *
    * @param bearerToken
    * @param eServiceId
    * @return
    */
  override def getEService(eServiceId: UUID)(bearerToken: String): Future[EService] = {
    val request: ApiRequest[EService] = api.getEService(eServiceId.toString)(BearerToken(bearerToken))
    invoker.invoke(request, "Retrieving E-Service", handleCommonErrors(s"eservice $eServiceId"))
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
