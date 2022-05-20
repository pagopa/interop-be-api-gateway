package it.pagopa.interop.apigateway.service.impl

import it.pagopa.interop.apigateway.service.{PartyManagementInvoker, PartyManagementService}
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.interop.partymanagement.client.api.PartyApi
import it.pagopa.interop.partymanagement.client.invoker.{ApiError, ApiRequest, BearerToken}
import it.pagopa.interop.partymanagement.client.model._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}

import java.util.UUID
import scala.concurrent.Future

class PartyManagementServiceImpl(invoker: PartyManagementInvoker, api: PartyApi) extends PartyManagementService {

  implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getInstitution(
    institutionId: UUID
  )(bearerToken: String)(implicit context: Seq[(String, String)]): Future[Institution] = {
    val request: ApiRequest[Institution] = api.getInstitutionById(institutionId)(BearerToken(bearerToken))
    invoker.invoke(request, "Retrieve Institution", handleCommonErrors(s"institution $institutionId"))
  }

  private[service] def handleCommonErrors[T](
    resource: String
  ): (ContextFieldsToLog, LoggerTakingImplicit[ContextFieldsToLog], String) => PartialFunction[Throwable, Future[T]] = {
    (context, logger, msg) =>
      {
        case ex @ ApiError(code, message, _, _, _) if code == 404 =>
          logger.error(s"$msg. code > $code - message > $message - ${ex.getMessage}")(context)
          Future.failed(GenericComponentErrors.ResourceNotFoundError(resource))
        case ex                                                   =>
          logger.error(s"$msg. Error: ${ex.getMessage}")(context)
          Future.failed(ex)
      }
  }

}
