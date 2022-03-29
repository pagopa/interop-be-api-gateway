package it.pagopa.interop.apigateway.service

import it.pagopa.interop.apigateway.service.impl.extractHeadersWithOptionalCorrelationId
import it.pagopa.interop.commons.utils.{BEARER, CORRELATION_ID_HEADER}
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ExtractingHeadersSpec extends AnyWordSpec with Matchers with EitherValues {

  "Extracting Headers" should {

    "return contexts with random correlation id if not passed in input" in {
      // given
      val contexts  = Seq(BEARER -> "111")
      // when
      val extracted = extractHeadersWithOptionalCorrelationId(contexts)
      // then
      extracted.value shouldBe a[(_, _, Option[_])]
    }

    "return contexts with correlation id if passed in input" in {
      // given
      val contexts  = Seq(BEARER -> "111", CORRELATION_ID_HEADER -> "yada-yada")
      // when
      val extracted = extractHeadersWithOptionalCorrelationId(contexts)
      // then
      extracted.value shouldBe ("111", "yada-yada", None)
    }
  }
}
