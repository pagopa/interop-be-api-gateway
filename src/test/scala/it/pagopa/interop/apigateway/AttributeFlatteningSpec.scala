package it.pagopa.interop.apigateway

import it.pagopa.interop.agreementmanagement.client.model.{VerifiedAttribute => AgreementManagementApiVerifiedAttribute}
import it.pagopa.interop.apigateway.api.impl.EnrichedEService
import it.pagopa.interop.apigateway.model.AttributeValidity.VALID
import it.pagopa.interop.apigateway.model.AttributeValidityState
import it.pagopa.interop.catalogmanagement.client.model.{
  Attribute,
  AttributeValue,
  Attributes,
  EServiceTechnology,
  EService => CatalogManagementApiEService
}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

class AttributeFlatteningSpec extends AnyWordSpec with Matchers {

  "Agreement attributes" should {

    val attributeId1 = UUID.randomUUID()
    val attributeId2 = UUID.randomUUID()
    val attributeId3 = UUID.randomUUID()
    val attributeId4 = UUID.randomUUID()
    val attributeId5 = UUID.randomUUID()
    val attributeId6 = UUID.randomUUID()
    val attributeId7 = UUID.randomUUID()

    "be retrieved flattened" in {
      val attributes = Set(
        AgreementManagementApiVerifiedAttribute(
          id = attributeId1,
          verified = Some(true),
          verificationDate = None,
          validityTimespan = None
        ),
        AgreementManagementApiVerifiedAttribute(
          id = attributeId2,
          verified = Some(true),
          verificationDate = None,
          validityTimespan = None
        ),
        AgreementManagementApiVerifiedAttribute(
          id = attributeId3,
          verified = Some(true),
          verificationDate = None,
          validityTimespan = None
        ),
        AgreementManagementApiVerifiedAttribute(
          id = attributeId4,
          verified = Some(true),
          verificationDate = None,
          validityTimespan = None
        ),
        AgreementManagementApiVerifiedAttribute(
          id = attributeId5,
          verified = Some(true),
          verificationDate = None,
          validityTimespan = None
        ),
        AgreementManagementApiVerifiedAttribute(
          id = attributeId6,
          verified = Some(true),
          verificationDate = None,
          validityTimespan = None
        ),
        AgreementManagementApiVerifiedAttribute(
          id = attributeId7,
          verified = Some(true),
          verificationDate = None,
          validityTimespan = None
        )
      )

      val verified = Seq(
        Attribute(single = Some(AttributeValue(attributeId1, false)), group = None),
        Attribute(
          single = None,
          group = Some(
            Seq(
              AttributeValue(attributeId2, true),
              AttributeValue(attributeId3, true),
              AttributeValue(attributeId4, true)
            )
          )
        ),
        Attribute(
          single = None,
          group = Some(
            Seq(
              AttributeValue(attributeId5, true),
              AttributeValue(attributeId6, true),
              AttributeValue(attributeId7, true)
            )
          )
        )
      )

      val service = CatalogManagementApiEService(
        id = UUID.randomUUID(),
        producerId = UUID.randomUUID(),
        name = "pippo",
        description = "pluto",
        technology = EServiceTechnology.REST,
        attributes = Attributes(certified = Seq.empty, declared = Seq.empty, verified = verified),
        descriptors = Seq.empty
      )

      val result: Set[AttributeValidityState] = service.attributeUUIDSummary(Set.empty, attributes, Set.empty)
      result should contain only (
        AttributeValidityState(attributeId1, VALID),
        AttributeValidityState(attributeId2, VALID),
        AttributeValidityState(attributeId3, VALID),
        AttributeValidityState(attributeId4, VALID),
        AttributeValidityState(attributeId5, VALID),
        AttributeValidityState(attributeId6, VALID),
        AttributeValidityState(attributeId7, VALID)
      )
    }
  }
}
