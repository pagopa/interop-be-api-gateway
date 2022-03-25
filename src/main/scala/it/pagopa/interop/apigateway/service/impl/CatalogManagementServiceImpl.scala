package it.pagopa.interop.apigateway.service.impl

import it.pagopa.interop.apigateway.service.{CatalogManagementInvoker, CatalogManagementService}
import it.pagopa.interop.catalogmanagement.client.api.EServiceApi
import it.pagopa.interop.catalogmanagement.client.invoker.{ApiError, BearerToken}
import it.pagopa.interop.catalogmanagement.client.model.EService
import it.pagopa.interop.commons.utils.TypeConversions.EitherOps
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.interop.commons.utils.extractHeaders
import org.slf4j.{Logger, LoggerFactory}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class CatalogManagementServiceImpl(invoker: CatalogManagementInvoker, api: EServiceApi)(implicit ec: ExecutionContext)
    extends CatalogManagementService {

  implicit val logger: Logger = LoggerFactory.getLogger(this.getClass)

  /** Returns the expected audience defined by the producer of the corresponding agreementId.
    *
    * @param contexts
    * @param eServiceId
    * @return
    */
  override def getEService(eServiceId: UUID)(contexts: Seq[(String, String)]): Future[EService] = {
    for {
      (bearerToken, correlationId, ip) <- extractHeaders(contexts).toFuture
      request = api.getEService(xCorrelationId = correlationId, eServiceId.toString, xForwardedFor = ip)(
        BearerToken(bearerToken)
      )
      result <- invoker.invoke(request, "Retrieving E-Service", handleCommonErrors(s"eservice $eServiceId"))
    } yield result
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
