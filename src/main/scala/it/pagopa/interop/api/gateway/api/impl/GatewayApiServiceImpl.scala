package it.pagopa.interop.api.gateway.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.Logger
import it.pagopa.interop.api.gateway.error.GatewayErrors._
import it.pagopa.interop.be.gateway.api.GatewayApiService
import it.pagopa.interop.be.gateway.model._
import it.pagopa.pdnd.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.pdnd.interop.commons.utils.AkkaUtils._
import it.pagopa.pdnd.interop.commons.utils.TypeConversions._
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.{Failure, Success}
// import it.pagopa.pdnd.interop.uservice.agreementmanagement.client.model.{Agreement => AgreementManagementApiAgreement}
import akka.http.scaladsl.model.StatusCodes
import it.pagopa.interop.api.gateway.service.{
  AgreementManagementService,
  CatalogManagementService,
  PartyManagementService
}
import it.pagopa.pdnd.interop.commons.utils.errors.GenericComponentErrors

import scala.concurrent.ExecutionContext

class GatewayApiServiceImpl(
  partyManagementService: PartyManagementService,
  agreementManagementService: AgreementManagementService,
  catalogManagementService: CatalogManagementService
)(implicit ec: ExecutionContext)
    extends GatewayApiService {

  val logger = Logger.takingImplicit[ContextFieldsToLog](LoggerFactory.getLogger(this.getClass))

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
      rawAgreement   <- agreementManagementService.getAgreementById(agreementId)(bearerToken)

      agreement <- Either
        .cond(
          (organizationId == rawAgreement.producerId || organizationId == rawAgreement.consumerId),
          rawAgreement,
          AgreementNotFoundForOrganizationError
        )
        .toFuture

      eservice <- catalogManagementService.getEService(agreement.eserviceId)(bearerToken)
      producer <- partyManagementService.getOrganization(agreement.producerId)(bearerToken)
      consumer <- partyManagementService.getOrganization(agreement.consumerId)(bearerToken)

    } yield agreement.toModel(eservice.toModel, producer.toModel, consumer.toModel)

    onComplete(result) {
      case Success(agr) =>
        getAgreement200(agr)
      case Failure(AgreementNotFoundForOrganizationError) =>
        logger.error(
          "Error while getting agreement id {}: {}",
          agreementId,
          AgreementNotFoundForOrganizationError.getMessage
        )
        getAgreement400(problemOf(StatusCodes.InternalServerError, AgreementNotFoundForOrganizationError))
      case Failure(_) =>
        getAgreement400(problemOf(StatusCodes.InternalServerError, GenericComponentErrors.ResourceNotFoundError("1")))
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
  ): Route = ???

  /** Code: 200, Message: Attribute retrieved, DataType: Attribute
    * Code: 400, Message: Bad request, DataType: Problem
    * Code: 401, Message: Unauthorized, DataType: Problem
    * Code: 404, Message: Agreement not found, DataType: Problem
    */
  override def getAttribute(attributeId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerAttribute: ToEntityMarshaller[Attribute],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = ???

  /** Code: 200, Message: EService retrieved, DataType: EService
    * Code: 400, Message: Bad request, DataType: Problem
    * Code: 401, Message: Unauthorized, DataType: Problem
    * Code: 404, Message: Agreement not found, DataType: Problem
    */
  override def getEService(eserviceId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerEService: ToEntityMarshaller[EService]
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

  /** Code: 200, Message: Il server ha ritornato lo status. In caso di problemi ritorna sempre un problem+json. , DataType: Problem
    */
  override def getStatus()(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = ???
}
