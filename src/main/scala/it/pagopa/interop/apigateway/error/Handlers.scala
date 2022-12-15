package it.pagopa.interop.apigateway.error

import akka.http.scaladsl.server.StandardRoute
import com.typesafe.scalalogging.LoggerTakingImplicit
import it.pagopa.interop.apigateway.error.GatewayErrors._
import it.pagopa.interop.commons.logging.ContextFieldsToLog
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.OperationForbidden
import it.pagopa.interop.commons.utils.errors.{AkkaResponses, ServiceCode}

import scala.util.{Failure, Try}

object Handlers extends AkkaResponses {

  implicit val serviceCode: ServiceCode = ServiceCode("013")

  def handleGetAgreementError(logMessage: String, agreementId: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: AgreementNotFound)          => notFound(ex, logMessage)
    case Failure(ex: InvalidAgreementState.type) =>
      logger.warn(s"Root cause for $logMessage", ex)
      notFound(AgreementNotFound(agreementId), logMessage)
    case Failure(ex)                             => internalServerError(ex, logMessage)
  }

  def handleGetAgreementsError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: ProducerAndConsumerParamMissing.type) => badRequest(ex, logMessage)
    case Failure(ex: OperationForbidden.type)              => forbidden(ex, logMessage)
    case Failure(ex)                                       => internalServerError(ex, logMessage)
  }

  def handleGetAttributeError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: AttributeNotFound) => notFound(ex, logMessage)
    case Failure(ex)                    => internalServerError(ex, logMessage)
  }

  def handleGetEServiceError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: EServiceNotFound)           => notFound(ex, logMessage)
    case Failure(ex: MissingAvailableDescriptor) =>
      logger.warn(s"Root cause for $logMessage", ex)
      notFound(EServiceNotFound(ex.eServiceId), logMessage)
    case Failure(ex)                             => internalServerError(ex, logMessage)
  }

  def handleGetEServiceDescriptorError(logMessage: String, eServiceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: EServiceNotFound)           => notFound(ex, logMessage)
    case Failure(ex: EServiceDescriptorNotFound) => notFound(ex, logMessage)
    case Failure(ex: UnexpectedDescriptorState)  =>
      logger.warn(s"Root cause for $logMessage", ex)
      notFound(EServiceDescriptorNotFound(eServiceId, descriptorId), logMessage)
    case Failure(ex)                             => internalServerError(ex, logMessage)
  }

  def handleGetEServiceDescriptorsError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: EServiceNotFound) => notFound(ex, logMessage)
    case Failure(ex)                   => internalServerError(ex, logMessage)
  }

  def handleGetOrganizationEServicesError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: TenantByOriginNotFound)    => notFound(ex, logMessage)
    case Failure(ex: AttributeByOriginNotFound) => notFound(ex, logMessage)
    case Failure(ex)                            => internalServerError(ex, logMessage)
  }

  def handleGetOrganizationError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: TenantNotFound) => notFound(ex, logMessage)
    case Failure(ex)                 => internalServerError(ex, logMessage)
  }

  def handleGetAgreementAttributesError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: AgreementNotFound) => notFound(ex, logMessage)
    case Failure(ex)                    => internalServerError(ex, logMessage)
  }

  def handleGetAgreementByPurposeError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: PurposeNotFound)                              => notFound(ex, logMessage)
    case Failure(ex: ActiveAgreementByEServiceAndConsumerNotFound) => notFound(ex, logMessage)
    case Failure(ex)                                               => internalServerError(ex, logMessage)
  }

  def handleGetPurposeError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: OperationForbidden.type)     => forbidden(ex, logMessage)
    case Failure(ex: PurposeNotFound)             => notFound(ex, logMessage)
    case Failure(ex: MissingActivePurposeVersion) =>
      logger.warn(s"Root cause for $logMessage", ex)
      notFound(PurposeNotFound(ex.purposeId), logMessage)
    case Failure(ex)                              => internalServerError(ex, logMessage)
  }

  def handleGetAgreementPurposesError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: AgreementNotFound) => notFound(ex, logMessage)
    case Failure(ex)                    => internalServerError(ex, logMessage)
  }

  def handleGetClientError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: OperationForbidden.type) => forbidden(ex, logMessage)
    case Failure(ex: ClientNotFound)          => notFound(ex, logMessage)
    case Failure(ex)                          => internalServerError(ex, logMessage)
  }

  def handleGetEventsFromIdError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = { case Failure(ex) =>
    internalServerError(ex, logMessage)
  }

  def handleGetEServicesEventsFromIdError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = { case Failure(ex) =>
    internalServerError(ex, logMessage)
  }
  def handleUpsertTenantError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: TenantProcessBadRequest) => badRequest(ex, logMessage)
    case Failure(ex: OperationForbidden.type) => forbidden(ex, logMessage)
    case Failure(ex: InstitutionNotFound)     => notFound(ex, logMessage)
    case Failure(ex)                          => internalServerError(ex, logMessage)
  }

  def handleRevokeTenantError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: TenantProcessBadRequest) => badRequest(ex, logMessage)
    case Failure(ex: OperationForbidden.type) => forbidden(ex, logMessage)
    case Failure(ex: TenantAttributeNotFound) => notFound(ex, logMessage)
    case Failure(ex)                          => internalServerError(ex, logMessage)
  }

  def handleCreateCertifiedAttributeError(logMessage: String)(implicit
    contexts: Seq[(String, String)],
    logger: LoggerTakingImplicit[ContextFieldsToLog]
  ): PartialFunction[Try[_], StandardRoute] = {
    case Failure(ex: OrganizationIsNotACertifier) => forbidden(ex, logMessage)
    case Failure(ex: AttributeAlreadyExists)      => conflict(ex, logMessage)
    case Failure(ex)                              => internalServerError(ex, logMessage)
  }
}
