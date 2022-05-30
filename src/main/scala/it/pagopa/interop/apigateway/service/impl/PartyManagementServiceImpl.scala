package it.pagopa.interop.apigateway.service.impl

import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.apigateway.service.{PartyManagementApiKeyValue, PartyManagementInvoker, PartyManagementService}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.utils.AkkaUtils.getUidFuture
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.interop.selfcare.partymanagement.client.api.PartyApi
import it.pagopa.interop.selfcare.partymanagement.client.invoker.ApiError
import it.pagopa.interop.selfcare.partymanagement.client.model._

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class PartyManagementServiceImpl(invoker: PartyManagementInvoker, api: PartyApi)(implicit
  partyManagementApiKeyValue: PartyManagementApiKeyValue
) extends PartyManagementService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getInstitution(
    institutionId: UUID
  )(implicit contexts: Seq[(String, String)], ec: ExecutionContext): Future[Institution] = for {
    uid <- getUidFuture(contexts)
    request = api.getInstitutionById(institutionId)(uid)
    result <- invoker.invoke(request, "Retrieve Institution", handleCommonErrors(s"institution $institutionId"))
  } yield result

  private[service] def handleCommonErrors[T](
    resource: String
  ): (ContextFieldsToLog, LoggerTakingImplicit[ContextFieldsToLog], String) => PartialFunction[Throwable, Future[T]] = {
    (contexts, logger, msg) =>
      {
        case ex @ ApiError(code, message, _, _, _) if code == 404 =>
          logger.error(s"$msg. code > $code - message > $message - ${ex.getMessage}")(contexts)
          Future.failed(GenericComponentErrors.ResourceNotFoundError(resource))
        case ex                                                   =>
          logger.error(s"$msg. Error: ${ex.getMessage}")(contexts)
          Future.failed(ex)
      }
  }

}
