package it.pagopa.interop.api.gateway.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.interop.be.gateway.api.GatewayApiMarshaller
import it.pagopa.interop.be.gateway.model._

object GatewayApiMarshallerImpl extends GatewayApiMarshaller {
  override implicit def toEntityMarshallerPurpose: ToEntityMarshaller[Purpose] = sprayJsonMarshaller[Purpose]

  override implicit def toEntityMarshallerAgreements: ToEntityMarshaller[Agreements] = sprayJsonMarshaller[Agreements]

  override implicit def toEntityMarshallerAttribute: ToEntityMarshaller[Attribute] = sprayJsonMarshaller[Attribute]

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]

  override implicit def toEntityMarshallerAgreement: ToEntityMarshaller[Agreement] = sprayJsonMarshaller[Agreement]

  override implicit def toEntityMarshallerEService: ToEntityMarshaller[EService] = sprayJsonMarshaller[EService]
}
