package it.pagopa.interop.apigateway.api.impl

import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LoggerTakingImplicit
import it.pagopa.interop.apigateway.error.GatewayErrors._
import it.pagopa.interop.commons.logging.ContextFieldsToLog
import it.pagopa.interop.commons.utils.errors.GenericComponentErrors.OperationForbidden
import it.pagopa.interop.commons.utils.errors.{AkkaResponses, ServiceCode}

import scala.util.{Failure, Success, Try}

object ResponseHandlers extends AkkaResponses {

  implicit val serviceCode: ServiceCode = ServiceCode("013")

  def getAgreementResponse[T](logMessage: String, agreementId: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                              => success(s)
      case Failure(ex: AgreementNotFound)          => notFound(ex, logMessage)
      case Failure(ex: InvalidAgreementState.type) =>
        logger.warn(s"Root cause for $logMessage", ex)
        notFound(AgreementNotFound(agreementId), logMessage)
      case Failure(ex)                             => internalServerError(ex, logMessage)
    }

  def getAgreementsResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                        => success(s)
      case Failure(ex: ProducerAndConsumerParamMissing.type) => badRequest(ex, logMessage)
      case Failure(ex: OperationForbidden.type)              => forbidden(ex, logMessage)
      case Failure(ex)                                       => internalServerError(ex, logMessage)
    }

  def getAttributeResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                     => success(s)
      case Failure(ex: AttributeNotFound) => notFound(ex, logMessage)
      case Failure(ex)                    => internalServerError(ex, logMessage)
    }

  def getEServiceResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                              => success(s)
      case Failure(ex: EServiceNotFound)           => notFound(ex, logMessage)
      case Failure(ex: MissingAvailableDescriptor) =>
        logger.warn(s"Root cause for $logMessage", ex)
        notFound(EServiceNotFound(ex.eServiceId), logMessage)
      case Failure(ex)                             => internalServerError(ex, logMessage)
    }

  def getEServiceDescriptorResponse[T](logMessage: String, eServiceId: String, descriptorId: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                              => success(s)
      case Failure(ex: EServiceNotFound)           => notFound(ex, logMessage)
      case Failure(ex: EServiceDescriptorNotFound) => notFound(ex, logMessage)
      case Failure(ex: UnexpectedDescriptorState)  =>
        logger.warn(s"Root cause for $logMessage", ex)
        notFound(EServiceDescriptorNotFound(eServiceId, descriptorId), logMessage)
      case Failure(ex)                             => internalServerError(ex, logMessage)
    }

  def getEServiceDescriptorsResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                    => success(s)
      case Failure(ex: EServiceNotFound) => notFound(ex, logMessage)
      case Failure(ex)                   => internalServerError(ex, logMessage)
    }

  def getOrganizationEServicesResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                             => success(s)
      case Failure(ex: TenantByOriginNotFound)    => notFound(ex, logMessage)
      case Failure(ex: AttributeByOriginNotFound) => notFound(ex, logMessage)
      case Failure(ex)                            => internalServerError(ex, logMessage)
    }

  def getOrganizationResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                  => success(s)
      case Failure(ex: TenantNotFound) => notFound(ex, logMessage)
      case Failure(ex)                 => internalServerError(ex, logMessage)
    }

  def getAgreementAttributesResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                     => success(s)
      case Failure(ex: AgreementNotFound) => notFound(ex, logMessage)
      case Failure(ex)                    => internalServerError(ex, logMessage)
    }

  def getAgreementByPurposeResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                                => success(s)
      case Failure(ex: PurposeNotFound)                              => notFound(ex, logMessage)
      case Failure(ex: ActiveAgreementByEServiceAndConsumerNotFound) => notFound(ex, logMessage)
      case Failure(ex)                                               => internalServerError(ex, logMessage)
    }

  def getPurposeResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                               => success(s)
      case Failure(ex: OperationForbidden.type)     => forbidden(ex, logMessage)
      case Failure(ex: PurposeNotFound)             => notFound(ex, logMessage)
      case Failure(ex: MissingActivePurposeVersion) =>
        logger.warn(s"Root cause for $logMessage", ex)
        notFound(PurposeNotFound(ex.purposeId), logMessage)
      case Failure(ex)                              => internalServerError(ex, logMessage)
    }

  def getAgreementPurposesResponse[T](logMessage: String)(success: T => Route, emptyResponse: T)(
    result: Try[T]
  )(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                => success(s)
      case Failure(_: MissingActivePurposesVersions) => success(emptyResponse)
      case Failure(ex: AgreementNotFound)            => notFound(ex, logMessage)
      case Failure(ex)                               => internalServerError(ex, logMessage)
    }

  def getClientResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                           => success(s)
      case Failure(ex: OperationForbidden.type) => forbidden(ex, logMessage)
      case Failure(ex: ClientNotFound)          => notFound(ex, logMessage)
      case Failure(ex)                          => internalServerError(ex, logMessage)
    }

  def getEventsFromIdResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)  => success(s)
      case Failure(ex) =>
        internalServerError(ex, logMessage)
    }

  def getEservicesEventsFromIdResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)  => success(s)
      case Failure(ex) => internalServerError(ex, logMessage)
    }

  def getKeysEventsFromIdResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)  => success(s)
      case Failure(ex) => internalServerError(ex, logMessage)
    }

  def upsertTenantResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                           => success(s)
      case Failure(ex: TenantProcessBadRequest) => badRequest(ex, logMessage)
      case Failure(ex: OperationForbidden.type) => forbidden(ex, logMessage)
      case Failure(ex: InstitutionNotFound)     => notFound(ex, logMessage)
      case Failure(ex)                          => internalServerError(ex, logMessage)
    }

  def revokeTenantAttributeResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                           => success(s)
      case Failure(ex: TenantProcessBadRequest) => badRequest(ex, logMessage)
      case Failure(ex: OperationForbidden.type) => forbidden(ex, logMessage)
      case Failure(ex: TenantAttributeNotFound) => notFound(ex, logMessage)
      case Failure(ex)                          => internalServerError(ex, logMessage)
    }

  def createCertifiedAttributeResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                          => success(s)
      case Failure(ex: AttributeAlreadyExists) => conflict(ex, logMessage)
      case Failure(ex)                         => internalServerError(ex, logMessage)
    }

  def getKeyJWKfromKIdResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(key)             => success(key)
      case Failure(ex: KeyNotFound) => notFound(ex, logMessage)
      case Failure(ex)              => internalServerError(ex, logMessage)
    }

  def getEservicesResponse[T](logMessage: String)(
    success: T => Route
  )(result: Try[T])(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)  => success(s)
      case Failure(ex) => internalServerError(ex, logMessage)
    }

  def getPurposesByEserviceAndConsumerResponse[T](logMessage: String)(success: T => Route, emptyResponse: T)(
    result: Try[T]
  )(implicit contexts: Seq[(String, String)], logger: LoggerTakingImplicit[ContextFieldsToLog]): Route =
    result match {
      case Success(s)                                => success(s)
      case Failure(_: MissingActivePurposesVersions) => success(emptyResponse)
      case Failure(ex)                               => internalServerError(ex, logMessage)
    }
}
