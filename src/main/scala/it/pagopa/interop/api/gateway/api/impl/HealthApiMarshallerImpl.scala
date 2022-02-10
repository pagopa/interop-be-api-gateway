package it.pagopa.interop.api.gateway.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.interop.be.gateway.api.HealthApiMarshaller
import it.pagopa.interop.be.gateway.model.Problem
import spray.json.DefaultJsonProtocol

object HealthApiMarshallerImpl extends HealthApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = sprayJsonMarshaller[Problem]
}
