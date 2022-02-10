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
  CatalogManagementService
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
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class GatewayApiServiceImpl(
  agreementManagementService: AgreementManagementService,
  catalogManagementService: CatalogManagementService,
  attributeRegistryManagementService: AttributeRegistryManagementService
)(implicit ec: ExecutionContext)
    extends GatewayApiService {

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
      agreement <-
        agreementManagementService
          .getAgreementById(agreementId)(bearerToken)
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
    * Code: 404, Message: Agreement not found, DataType: Problem
    */
  override def getEService(eserviceId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
  ): Route = {
    val result: Future[EService] = for {
      bearerToken  <- getFutureBearer(contexts)
      eserviceUUID <- eserviceId.toFutureUUID
      eservice     <- catalogManagementService.getEService(eserviceUUID)(bearerToken)
    } yield eservice.toModel

    onComplete(result) {
      case Success(eservice) =>
        getEService200(eservice)
      case Failure(EServiceNotFoundForOrganizationError) =>
        logger.error(
          "Error while getting e-service id {}: {}",
          eserviceId,
          EServiceNotFoundForOrganizationError.getMessage
        )
        getAgreement404(problemOf(StatusCodes.NotFound, EServiceNotFoundForOrganizationError))
      case Failure(_) =>
        complete(
          StatusCodes.InternalServerError,
          problemOf(StatusCodes.InternalServerError, GenericComponentErrors.ResourceNotFoundError("1"))
        )
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

      rawAgreement <-
        agreementManagementService
          .getAgreementById(agreementId)(bearerToken)
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
  ): Route = ???

  /** Code: 200, Message: Purpose retrieved, DataType: Purpose
    * Code: 400, Message: Bad request, DataType: Problem
    * Code: 401, Message: Unauthorized, DataType: Problem
    * Code: 404, Message: Purpose not found, DataType: Problem
    */
  override def getPurpose(purposeId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurpose: ToEntityMarshaller[Purpose],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = ???

  /** Code: 200, Message: Purposes retrieved, DataType: Seq[Purpose]
    * Code: 400, Message: Bad request, DataType: Problem
    * Code: 401, Message: Unauthorized, DataType: Problem
    * Code: 404, Message: Purposes not found, DataType: Problem
    */
  override def getAgreementPurposes(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerPurposes: ToEntityMarshaller[Purposes],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = ???
}
