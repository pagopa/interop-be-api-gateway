package it.pagopa.interop.apigateway.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, onComplete}
import akka.http.scaladsl.server.{Route, StandardRoute}
import cats.implicits._
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.apigateway.api.GatewayApiService
import it.pagopa.interop.apigateway.error.GatewayErrors._
import it.pagopa.interop.apigateway.model._
import it.pagopa.interop.apigateway.service._
import it.pagopa.pdnd.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.pdnd.interop.commons.utils.AkkaUtils._
import it.pagopa.pdnd.interop.commons.utils.TypeConversions._
import it.pagopa.pdnd.interop.commons.utils.errors.GenericComponentErrors
import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{
  AgreementState => AgreementManagementApiAgreementState
}
import it.pagopa.pdnd.interop.uservice.purposemanagement.client.model.{Purpose => PurposeManagementApiPurpose}
import org.slf4j.LoggerFactory

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

final case class GatewayApiServiceImpl(
  partyManagementService: PartyManagementService,
  agreementManagementService: AgreementManagementService,
  catalogManagementService: CatalogManagementService,
  attributeRegistryManagementService: AttributeRegistryManagementService,
  purposeManagementService: PurposeManagementService
)(implicit ec: ExecutionContext)
    extends GatewayApiService {

  def internalServerError(message: String)(implicit c: ContextFieldsToLog): StandardRoute = {
    logger.error(message)
    complete(StatusCodes.InternalServerError, problemOf(StatusCodes.InternalServerError, InternalServerError))
  }

  val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](LoggerFactory.getLogger(this.getClass))

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
          .ensure(Forbidden)(agr => organizationId == agr.producerId || organizationId == agr.consumerId)
    } yield agreement.toModel

    onComplete(result) {
      case Success(agr) =>
        getAgreement200(agr)
      case Failure(Forbidden) =>
        logger.error("The user has no access to the requested agreement: {}", agreementId)
        getAgreement403(problemOf(StatusCodes.Forbidden, Forbidden))
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error("Error while getting agreement {}: {}", agreementId, ex.getMessage)
        getAgreement404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex) => internalServerError(s"Error while getting agreement - ${ex.getMessage}")
    }
  }

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
        case (None, None)                                 => Future.failed(InvalidAgreementsInput)
        //TODO! case (Some(x), Some(y)) if x === y
        case _ => Future.failed(Forbidden)
      }
      (prod, cons) = params
      agreementState <- state.traverse(AgreementManagementApiAgreementState.fromValue(_).toFuture)
      rawAgreements <- agreementManagementService.getAgreements(prod, cons, eserviceId, descriptorId, agreementState)(
        bearerToken
      )
      agreements = rawAgreements.map(_.toModel)
    } yield Agreements(agreements)

    onComplete(result) {
      case Success(agreements) => getAgreements200(agreements)
      case Failure(InvalidAgreementsInput) =>
        logger.error("Error while getting agreements: {}", InvalidAgreementsInput.getMessage)
        getAgreements400(problemOf(StatusCodes.BadRequest, InvalidAgreementsInput))
      case Failure(Forbidden) =>
        logger.error("The user has no access to the requested agreements")
        getAgreements403(problemOf(StatusCodes.Forbidden, Forbidden))
      case Failure(ex) => internalServerError(s"Error while getting agreements - ${ex.getMessage}")
    }
  }

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
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error("Error while getting attribute {}: {}", attributeId, ex.getMessage)
        getAttribute404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex) => internalServerError(s"Error while getting attribute - ${ex.getMessage}")
    }
  }

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
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error("Error while getting eservice {}: {}", eserviceId, ex.getMessage)
        getEService404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex: EServiceDescriptorNotFound) =>
        logger.error(ex.getMessage)
        getEService404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex) => internalServerError(s"Error while getting eservice - ${ex.getMessage}")
    }
  }

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
      case Success(organization) => getOrganization200(organization)
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error("Error while getting organization {}: {}", organizationId, ex.getMessage)
        getOrganization404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex) => internalServerError(s"Error while getting organization - ${ex.getMessage}")
    }
  }

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
          .ensure(Forbidden)(agr => organizationId == agr.producerId || organizationId == agr.consumerId)

      eservice <- catalogManagementService.getEService(rawAgreement.eserviceId)(bearerToken)

      attributeValidityStates = eservice.attributeUUIDSummary(
        certifiedFromParty = Set.empty,
        verifiedFromAgreement = rawAgreement.verifiedAttributes.toSet,
        declaredFromAgreement = Set.empty
      )

    } yield Attributes(attributeValidityStates)

    onComplete(result) {
      case Success(agr) => getAgreementAttributes200(agr)
      case Failure(Forbidden) =>
        logger.error("The user has no access to the requested attributes for agreement: {}", agreementId)
        getAgreementAttributes403(problemOf(StatusCodes.Forbidden, Forbidden))
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error("Error while getting attributes for agreement {}: {}", agreementId, ex.getMessage)
        getAgreementAttributes404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex) =>
        internalServerError(s"Error while getting attributes for agreement $agreementId - ${ex.getMessage}")
    }
  }

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
        .ensure(Forbidden)(a => a.consumerId == subjectUUID || a.producerId == subjectUUID)
    } yield agreement.toModel

    onComplete(result) {
      case Success(agreement) => getAgreementByPurpose200(agreement)
      case Failure(Forbidden) =>
        logger.error("The user has no access to the requested agreement for purpose {}", purposeId)
        getAgreementByPurpose403(problemOf(StatusCodes.Forbidden, Forbidden))
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error("Error while getting the requested agreement for purpose {} : {}", purposeId, ex.getMessage)
        getAgreementByPurpose404(problemOf(StatusCodes.NotFound, ex))
      case Failure(ex) =>
        internalServerError(s"Error while getting the requested agreement for purpose $purposeId - ${ex.getMessage}")
    }
  }

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
        .ifM(Future.successful(purpose), Future.failed(Forbidden))

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
      case Failure(Forbidden) =>
        logger.error("The user has no access to the requested purpose {}", purposeId)
        getPurpose403(problemOf(StatusCodes.Forbidden, Forbidden))
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error("Error while getting the requested purpose {} : {}", purposeId, ex.getMessage)
        getPurpose404(problemOf(StatusCodes.NotFound, ex))
      case Failure(e: MissingActivePurposeVersion) =>
        logger.error("Unable to find an active version of purpose {}", purposeId)
        getPurpose404(problemOf(StatusCodes.NotFound, e))
      case Failure(ex) =>
        internalServerError(s"Error while getting the requested purpose $purposeId - ${ex.getMessage}")
    }
  }

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
        .ensure(Forbidden)(a => a.consumerId == subjectUUID || a.producerId == subjectUUID)
      clientPurposes <- purposeManagementService.getPurposes(agreement.eserviceId, agreement.consumerId)(bearerToken)
      purposes       <- clientPurposes.toModel.toFuture
    } yield purposes

    onComplete(result) {
      case Success(agreement) => getAgreementPurposes200(agreement)
      case Failure(Forbidden) =>
        logger.error("The user has no access to the requested agreement {}", agreementId)
        getPurpose403(problemOf(StatusCodes.Forbidden, Forbidden))
      case Failure(ex: GenericComponentErrors.ResourceNotFoundError) =>
        logger.error("Error while getting the requested purposes for agreement {} : {}", agreementId, ex.getMessage)
        getPurpose404(problemOf(StatusCodes.NotFound, ex))
      case Failure(e: MissingActivePurposesVersions) =>
        logger.error(
          "Unable to find active or suspended versions for purposes {} in the agreement {}",
          e.purposesIds.mkString(", "),
          agreementId
        )
        getAgreementPurposes404(problemOf(StatusCodes.NotFound, e))
      case Failure(ex) =>
        internalServerError(s"Error while getting the requested purposes for agreement $agreementId - ${ex.getMessage}")
    }
  }
}
