package it.pagopa.interop.api.gateway.error

import it.pagopa.pdnd.interop.commons.utils.errors.ComponentError

object GatewayErrors {
  final case object AgreementNotFoundForOrganizationError
      extends ComponentError("0001", s"No agreement was found for your organization")
}
