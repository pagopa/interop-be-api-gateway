package it.pagopa.interop.apigateway

import akka.actor.ActorSystem
import it.pagopa.interop._
import it.pagopa.interop.apigateway.common.ApplicationConfiguration
import it.pagopa.interop.selfcare._

import scala.concurrent.ExecutionContext

package object service {
  type CatalogManagementInvoker           = catalogmanagement.client.invoker.ApiInvoker
  type PartyManagementInvoker             = partymanagement.client.invoker.ApiInvoker
  type AgreementManagementInvoker         = agreementmanagement.client.invoker.ApiInvoker
  type AuthorizationManagementInvoker     = authorizationmanagement.client.invoker.ApiInvoker
  type AttributeRegistryManagementInvoker = attributeregistrymanagement.client.invoker.ApiInvoker
  type PurposeManagementInvoker           = purposemanagement.client.invoker.ApiInvoker
  type NotifierInvoker                    = notifier.client.invoker.ApiInvoker

  type PartyManagementApiKeyValue = selfcare.partymanagement.client.invoker.ApiKeyValue

  object PartyManagementApiKeyValue {
    def apply(): PartyManagementApiKeyValue =
      partymanagement.client.invoker.ApiKeyValue(ApplicationConfiguration.partyManagementApiKey)
  }

  object AgreementManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): AgreementManagementInvoker =
      agreementmanagement.client.invoker.ApiInvoker(agreementmanagement.client.api.EnumsSerializers.all)
  }

  object AuthorizationManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem, blockingEc: ExecutionContext): AuthorizationManagementInvoker =
      authorizationmanagement.client.invoker.ApiInvoker(authorizationmanagement.client.api.EnumsSerializers.all)
  }

  object CatalogManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): CatalogManagementInvoker =
      catalogmanagement.client.invoker.ApiInvoker(catalogmanagement.client.api.EnumsSerializers.all)
  }

  object PartyManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): PartyManagementInvoker =
      partymanagement.client.invoker.ApiInvoker(partymanagement.client.api.EnumsSerializers.all)
  }

  object AttributeRegistryManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): AttributeRegistryManagementInvoker =
      attributeregistrymanagement.client.invoker.ApiInvoker(attributeregistrymanagement.client.api.EnumsSerializers.all)
  }

  object PurposeManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): PurposeManagementInvoker =
      purposemanagement.client.invoker.ApiInvoker(purposemanagement.client.api.EnumsSerializers.all)
  }

  object NotifierInvoker {
    def apply()(implicit actorSystem: ActorSystem): NotifierInvoker =
      notifier.client.invoker.ApiInvoker(partymanagement.client.api.EnumsSerializers.all)
  }

}
