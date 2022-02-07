package it.pagopa.interop.api.gateway.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Route
import it.pagopa.interop.be.gateway.api.GatewayApiService
import it.pagopa.interop.be.gateway.model._

class GatewayApiServiceImpl extends GatewayApiService {

  /** Code: 200, Message: Agreement retrieved, DataType: Agreement
    * Code: 400, Message: Bad request, DataType: Problem
    * Code: 401, Message: Unauthorized, DataType: Problem
    * Code: 404, Message: Agreement not found, DataType: Problem
    */
  override def getAgreement(agreementId: String)(implicit
    contexts: Seq[(String, String)],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem],
    toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]
  ): Route = ???

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
