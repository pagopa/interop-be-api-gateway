package it.pagopa.interop.apigateway.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import it.pagopa.interop.apigateway.api.GatewayApiMarshaller
import it.pagopa.interop.apigateway.model._
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller

object GatewayApiMarshallerImpl extends GatewayApiMarshaller {

  override implicit def fromEntityUnmarshallerAttributeSeed: FromEntityUnmarshaller[AttributeSeed] =
    sprayJsonUnmarshaller[AttributeSeed]

  override implicit def toEntityMarshallerPurpose: ToEntityMarshaller[Purpose] = sprayJsonMarshaller[Purpose]

  override implicit def toEntityMarshallerAgreements: ToEntityMarshaller[Agreements] = sprayJsonMarshaller[Agreements]

  override implicit def toEntityMarshallerAttributes: ToEntityMarshaller[Attributes] = sprayJsonMarshaller[Attributes]

  override implicit def toEntityMarshallerAttribute: ToEntityMarshaller[Attribute] = sprayJsonMarshaller[Attribute]

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem

  override implicit def toEntityMarshallerAgreement: ToEntityMarshaller[Agreement] = sprayJsonMarshaller[Agreement]

  override implicit def toEntityMarshallerEService: ToEntityMarshaller[EService] = sprayJsonMarshaller[EService]

  override implicit def toEntityMarshallerEServices: ToEntityMarshaller[EServices] = sprayJsonMarshaller[EServices]

  override implicit def toEntityMarshallerEServiceDescriptor: ToEntityMarshaller[EServiceDescriptor] =
    sprayJsonMarshaller[EServiceDescriptor]

  override implicit def toEntityMarshallerEServiceDescriptors: ToEntityMarshaller[EServiceDescriptors] =
    sprayJsonMarshaller[EServiceDescriptors]

  override implicit def toEntityMarshallerPurposes: ToEntityMarshaller[Purposes] = sprayJsonMarshaller[Purposes]

  override implicit def toEntityMarshallerOrganization: ToEntityMarshaller[Organization] =
    sprayJsonMarshaller[Organization]

  override implicit def toEntityMarshallerEvents: ToEntityMarshaller[Events] = sprayJsonMarshaller[Events]

  override implicit def toEntityMarshallerClient: ToEntityMarshaller[Client] = sprayJsonMarshaller[Client]

  override implicit def toEntityMarshallerJWK: ToEntityMarshaller[JWK] = sprayJsonMarshaller[JWK]

  override implicit def toEntityMarshallerCatalogEServices: ToEntityMarshaller[CatalogEServices] =
    sprayJsonMarshaller[CatalogEServices]
}
