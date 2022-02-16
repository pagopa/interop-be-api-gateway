package it.pagopa.interop.api.gateway.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.Route
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.api.gateway.error.GatewayErrors._
import it.pagopa.interop.api.gateway.service.{
  AgreementManagementService,
  AttributeRegistryManagementService,
  CatalogManagementService,
  PartyManagementService
}
import it.pagopa.interop.be.gateway.api.GatewayApiService
import it.pagopa.interop.be.gateway.model._
import it.pagopa.pdnd.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.pdnd.interop.commons.utils.AkkaUtils._
import it.pagopa.pdnd.interop.commons.utils.TypeConversions._
import it.pagopa.pdnd.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{
  AgreementState => AgreementManagementApiAgreementState
}
import it.pagopa.pdnd.interop.uservice.purposemanagement.client.model.{
  Purpose => PurposeManagementApiPurpose
// Purposes => PurposeManagementApiPurposes
}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import it.pagopa.interop.api.gateway.service.PurposeManagementService
import it.pagopa.interop.api.gateway.error.GatewayErrors.Unauthorized
import java.util.UUID
import it.pagopa.interop.api.gateway.error.GatewayErrors

class GatewayApiServiceImpl(
  partyManagementService: PartyManagementService,
  agreementManagementService: AgreementManagementService,
  catalogManagementService: CatalogManagementService,
  attributeRegistryManagementService: AttributeRegistryManagementService,
  purposeManagementService: PurposeManagementService
)(implicit ec: ExecutionContext)
    extends GatewayApiService {

  //TODO! Error Handling

  val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](LoggerFactory.getLogger(this.getClass))

  /** Code: 200, Message: Agreement retrieved, DataType: Agreement
    * Code: 400, Message: Bad request, DataType: Problem
    * Code: 401, Message: Unauthorized, DataType: Problem
    * Code: 404, Message: Agreement not found, DataType: Problem
    */
  override def getAgreement(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = {
    val result: Future[Agreement] = for {
      bearerToken    <- getFutureBearer(contexts)
      organizationId <- getSubFuture(contexts).flatMap(_.toFutureUUID)
      agreementUUID  <- agreementId.toFutureUUID
      agreement <-
        agreementManagementService
          .getAgreementById(agreementUUID)(bearerToken)
          .ensure(AgreementNotFound)(agr => organizationId == agr.producerId || organizationId == agr.consumerId)
    } yield agreement.toModel

    onComplete(result) {
      case Success(agr) =>
        getAgreement200(agr)
      case Failure(AgreementNotFound) =>
        logger.error("Error while getting agreement id {}: {}", agreementId, AgreementNotFound.getMessage)
        getAgreement400(problemOf(StatusCodes.InternalServerError, AgreementNotFound))
      case Failure(_) =>
        getAgreement400(problemOf(StatusCodes.InternalServerError, GenericComponentErrors.ResourceNotFoundError("1")))
    }
  }

  /** Code: 200, Message: A list of Agreement, DataType: Agreements
    * Code: 400, Message: Bad Request, DataType: Problem
    * Code: 401, Message: Unauthorized, DataType: Problem
    * Code: 403, Message: Forbidden, DataType: Problem
    */
  override def getAgreements(
    producerId: Option[String],
    consumerId: Option[String],
    eserviceId: Option[String],
    descriptorId: Option[String],
    state: Option[String]
  )(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAgreements: ToEntityMarshaller[Agreements],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Agreements] = for {
      bearerToken    <- getFutureBearer(contexts)
      organizationId <- getSubFuture(contexts)
      params <- (producerId, consumerId) match {
        case (producer @ Some(_), None)                   => Future.successful((producer, Some(organizationId)))
        case (None, consumer @ Some(_))                   => Future.successful((Some(organizationId), consumer))
        case (Some(`organizationId`), consumer @ Some(_)) => Future.successful((Some(organizationId), consumer))
        case (producer @ Some(_), Some(`organizationId`)) => Future.successful((producer, Some(organizationId)))
        case _                                            => Future.failed(InvalidAgreementsInput)
      }
      (prod, cons) = params
      agreementState <- state.traverse(AgreementManagementApiAgreementState.fromValue(_).toFuture)
      rawAgreements <- agreementManagementService.getAgreements(prod, cons, eserviceId, descriptorId, agreementState)(
        bearerToken
      )
      agreements = rawAgreements.map(_.toModel)
    } yield Agreements(agreements = agreements)

    onComplete(result) {
      case Success(agreements) => getAgreements200(agreements)
      case Failure(_)          => getAgreements400(problemOf(StatusCodes.InternalServerError, AgreementsError))
    }
  }

  /** Code: 200, Message: Attribute retrieved, DataType: Attribute
    * Code: 400, Message: Bad request, DataType: Problem
    * Code: 401, Message: Unauthorized, DataType: Problem
    * Code: 404, Message: Agreement not found, DataType: Problem
    */
  override def getAttribute(attributeId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Attribute] = for {
      bearerToken   <- getFutureBearer(contexts)
      attributeUUID <- attributeId.toFutureUUID
      rawAttribute  <- attributeRegistryManagementService.getAttributeById(attributeUUID)(bearerToken)
    } yield rawAttribute.toModel

    onComplete(result) {
      case Success(attribute) =>
        getAttribute200(attribute)
      case Failure(_) =>
        logger.error("Error while getting attribute id {} - Attribute not found", attributeId)
        getAttribute404(problemOf(StatusCodes.NotFound, AttributeNotFoundError(attributeId)))
    }
  }

  /** Code: 200, Message: EService retrieved, DataType: EService
    * Code: 400, Message: Bad request, DataType: Problem
    * Code: 401, Message: Unauthorized, DataType: Problem
    * Code: 404, Message: EService not found, DataType: Problem
    */
  override def getEService(eserviceId: String, descriptorId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = {
    val result: Future[EService] = for {
      bearerToken  <- getFutureBearer(contexts)
      eserviceUUID <- eserviceId.toFutureUUID
      eservice     <- catalogManagementService.getEService(eserviceUUID)(bearerToken)
      descriptor <- eservice.descriptors
        .find(_.id.toString == descriptorId)
        .toFuture(EServiceDescriptorNotFound(eserviceId, descriptorId))
    } yield eservice.toModel(descriptor)

    onComplete(result) {
      case Success(eservice) =>
        getEService200(eservice)
      case Failure(EServiceNotFoundForOrganizationError) =>
        logger.error(
          "Error while getting e-service id {}: {}",
          eserviceId,
          EServiceNotFoundForOrganizationError.getMessage
        )
        getEService404(problemOf(StatusCodes.NotFound, EServiceNotFoundForOrganizationError))
      case Failure(ex: EServiceDescriptorNotFound) =>
        logger.error("Error while getting e-service id {}: {}", eserviceId, ex.getMessage)
        getEService404(problemOf(StatusCodes.NotFound, ex))
      case Failure(_) =>
        complete(
          StatusCodes.InternalServerError,
          problemOf(StatusCodes.InternalServerError, GenericComponentErrors.ResourceNotFoundError("1"))
        )
    }
  }

  /** Code: 200, Message: Organization retrieved, DataType: Organization
    * Code: 400, Message: Bad request, DataType: Problem
    * Code: 401, Message: Unauthorized, DataType: Problem
    * Code: 404, Message: Organization not found, DataType: Problem
    */
  override def getOrganization(organizationId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerOrganization: ToEntityMarshaller[Organization],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    val result: Future[Organization] = for {
      bearerToken      <- getFutureBearer(contexts)
      organizationUUID <- organizationId.toFutureUUID
      organization     <- partyManagementService.getOrganization(organizationUUID)(bearerToken)
    } yield organization.toModel

    onComplete(result) {
      case Success(organization) =>
        getOrganization200(organization)
      case Failure(_) =>
        complete(StatusCodes.InternalServerError, problemOf(StatusCodes.InternalServerError, OrganizationError))
    }
  }

  /** Code: 200, Message: Attributes retrieved, DataType: Seq[UUID]
    * Code: 400, Message: Bad request, DataType: Problem
    * Code: 401, Message: Unauthorized, DataType: Problem
    * Code: 404, Message: Purposes not found, DataType: Problem
    */
  override def getAgreementAttributes(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttributes: ToEntityMarshaller[Attributes],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    val result: Future[Attributes] = for {
      bearerToken    <- getFutureBearer(contexts)
      organizationId <- getSubFuture(contexts).flatMap(_.toFutureUUID)
      agreementUUID  <- agreementId.toFutureUUID
      rawAgreement <-
        agreementManagementService
          .getAgreementById(agreementUUID)(bearerToken)
          .ensure(AgreementNotFound)(agr => organizationId == agr.producerId || organizationId == agr.consumerId)

      eservice <- catalogManagementService.getEService(rawAgreement.eserviceId)(bearerToken)

      attributeValidityStates = eservice.attributeUUIDSummary(
        certifiedFromParty = Set.empty,
        verifiedFromAgreement = rawAgreement.verifiedAttributes.toSet,
        declaredFromAgreement = Set.empty
      )

    } yield Attributes(attributeValidityStates)

    onComplete(result) {
      case Success(agr) => getAgreementAttributes200(agr)
      case Failure(AgreementNotFound) =>
        logger.error("Error while getting agreement id {}: {}", agreementId, AgreementNotFound.getMessage)
        getAgreementAttributes400(problemOf(StatusCodes.InternalServerError, AgreementNotFound))
      case Failure(_) =>
        complete(
          StatusCodes.InternalServerError,
          problemOf(StatusCodes.InternalServerError, GenericComponentErrors.ResourceNotFoundError("1"))
        )
    }
  }

  /** Code: 200, Message: Agreement retrieved, DataType: Agreement
    * Code: 400, Message: Bad request, DataType: Problem
    * Code: 401, Message: Unauthorized, DataType: Problem
    * Code: 404, Message: Agreement not found, DataType: Problem
    */
  override def getAgreementByPurpose(purposeId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = {
    val result: Future[Agreement] = for {
      bearerToken <- getFutureBearer(contexts)
      subjectUUID <- getSubFuture(contexts).flatMap(_.toFutureUUID)
      purposeUUID <- purposeId.toFutureUUID
      purpose     <- purposeManagementService.getPurpose(purposeUUID)(bearerToken)
      agreement <- agreementManagementService
        .getActiveOrSuspendedAgreementByConsumerAndEserviceId(purpose.consumerId, purpose.eserviceId)(bearerToken)
        .ensure(Unauthorized)(a => a.consumerId == subjectUUID || a.producerId == subjectUUID)
    } yield agreement.toModel

    onComplete(result) {
      case Success(agreement) => getAgreementByPurpose200(agreement)
      case Failure(AgreementNotFound) =>
        logger.error("Unable to find an active or suspended agreement for purpose {}", purposeId)
        getAgreement404(problemOf(StatusCodes.NotFound, AgreementNotFound))
      case Failure(Unauthorized) =>
        logger.error(
          "The user is not authorized to retrieve an active or suspended agreement for purpose {}",
          purposeId
        )
        getAgreement401(problemOf(StatusCodes.Unauthorized, Unauthorized))
      case Failure(_) =>
        complete(
          StatusCodes.InternalServerError,
          problemOf(StatusCodes.InternalServerError, GatewayErrors.InternalServerError)
        )
    }
  }

  /** Code: 200, Message: Purpose retrieved, DataType: Purpose
    * Code: 400, Message: Bad request, DataType: Problem
    * Code: 401, Message: Unauthorized, DataType: Problem
    * Code: 404, Message: Purpose not found, DataType: Problem
    */
  override def getPurpose(purposeId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurpose: ToEntityMarshaller[Purpose],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    def validatePurposeIfSubjectIsProducer(subject: UUID, purpose: PurposeManagementApiPurpose)(
      bearerToken: String
    ): Future[PurposeManagementApiPurpose] =
      catalogManagementService
        .getEService(purpose.eserviceId)(bearerToken)
        .map(_.producerId == subject)
        .ifM(Future.successful(purpose), Future.failed(Unauthorized))

    def getPurposeIfAuthorized(subject: UUID, purposeUUID: UUID)(
      bearerToken: String
    ): Future[PurposeManagementApiPurpose] = purposeManagementService
      .getPurpose(purposeUUID)(bearerToken)
      .flatMap(purpose =>
        if (purpose.consumerId == subject) Future.successful(purpose)
        else validatePurposeIfSubjectIsProducer(subject, purpose)(bearerToken)
      )

    val result: Future[Purpose] = for {
      bearerToken          <- getFutureBearer(contexts)
      subjectUUID          <- getSubFuture(contexts).flatMap(_.toFutureUUID)
      purposeUUID          <- purposeId.toFutureUUID
      purpose              <- getPurposeIfAuthorized(subjectUUID, purposeUUID)(bearerToken)
      actualPurposeVersion <- purpose.toModel.toFuture
    } yield actualPurposeVersion

    onComplete(result) {
      case Success(agr) => getPurpose200(agr)
      case Failure(e: MissingActivePurposeVersion) =>
        logger.error("Unable to find an active version of purpose {}", purposeId)
        getAgreement404(problemOf(StatusCodes.NotFound, e))
      case Failure(Unauthorized) =>
        logger.error("The user is not authorized to retrieve the purpose with id {}", purposeId)
        getAgreement401(problemOf(StatusCodes.Unauthorized, Unauthorized))
      case Failure(_) =>
        complete(
          StatusCodes.InternalServerError,
          problemOf(StatusCodes.InternalServerError, GenericComponentErrors.ResourceNotFoundError("1"))
        )
    }
  }

  /** Code: 200, Message: Purposes retrieved, DataType: Seq[Purpose]
    * Code: 400, Message: Bad request, DataType: Problem
    * Code: 401, Message: Unauthorized, DataType: Problem
    * Code: 404, Message: Purposes not found, DataType: Problem
    */
  override def getAgreementPurposes(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurposes: ToEntityMarshaller[Purposes],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {

    val result: Future[Purposes] = for {
      bearerToken   <- getFutureBearer(contexts)
      subjectUUID   <- getSubFuture(contexts).flatMap(_.toFutureUUID)
      agreementUUID <- agreementId.toFutureUUID
      agreement <- agreementManagementService
        .getAgreementById(agreementUUID)(bearerToken)
        .ensure(Unauthorized)(a => a.consumerId == subjectUUID || a.producerId == subjectUUID)
      clientPurposes <- purposeManagementService.getPurposes(agreement.eserviceId, agreement.consumerId)(bearerToken)
      purposes       <- clientPurposes.toModel.toFuture
    } yield purposes

    onComplete(result) {
      case Success(agreement) => getAgreementPurposes200(agreement)
      case Failure(e: MissingActivePurposesVersions) =>
        logger.error(
          "Unable to find active or suspended versions for purposes {} in the agreement {}",
          e.purposesIds.mkString(", "),
          agreementId
        )
        getAgreementPurposes404(problemOf(StatusCodes.NotFound, e))
      case Failure(Unauthorized) =>
        logger.error("The user is not authorized to retrieve the purposes for the agreement {}", agreementId)
        getAgreementPurposes401(problemOf(StatusCodes.Unauthorized, Unauthorized))
      case Failure(_) =>
        complete(
          StatusCodes.InternalServerError,
          problemOf(StatusCodes.InternalServerError, GatewayErrors.InternalServerError)
        )
    }
  }
}
