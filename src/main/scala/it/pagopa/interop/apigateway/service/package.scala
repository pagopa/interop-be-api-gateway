package it.pagopa.interop.apigateway

import akka.actor.ActorSystem
import it.pagopa.interop._

import scala.concurrent.ExecutionContextExecutor

package object service {
  type CatalogProcessInvoker           = catalogprocess.client.invoker.ApiInvoker
  type AgreementProcessInvoker         = agreementprocess.client.invoker.ApiInvoker
  type AuthorizationProcessInvoker     = authorizationprocess.client.invoker.ApiInvoker
  type AttributeRegistryProcessInvoker = attributeregistryprocess.client.invoker.ApiInvoker
  type PurposeProcessInvoker           = purposeprocess.client.invoker.ApiInvoker
  type NotifierInvoker                 = notifier.client.invoker.ApiInvoker
  type TenantProcessInvoker            = tenantprocess.client.invoker.ApiInvoker
  type PartyRegistryInvoker            = partyregistryproxy.client.invoker.ApiInvoker

  object AgreementProcessInvoker {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ActorSystem): AgreementProcessInvoker =
      agreementprocess.client.invoker.ApiInvoker(agreementprocess.client.api.EnumsSerializers.all, blockingEc)
  }

  object AuthorizationProcessInvoker {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ActorSystem): AuthorizationProcessInvoker =
      authorizationprocess.client.invoker
        .ApiInvoker(authorizationprocess.client.api.EnumsSerializers.all, blockingEc)
  }

  object CatalogProcessInvoker {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ActorSystem): CatalogProcessInvoker =
      catalogprocess.client.invoker.ApiInvoker(catalogprocess.client.api.EnumsSerializers.all, blockingEc)
  }

  object AttributeRegistryProcessInvoker {
    def apply(
      blockingEc: ExecutionContextExecutor
    )(implicit actorSystem: ActorSystem): AttributeRegistryProcessInvoker =
      attributeregistryprocess.client.invoker
        .ApiInvoker(attributeregistryprocess.client.api.EnumsSerializers.all, blockingEc)
  }

  object PartyRegistryInvoker  {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ActorSystem): PartyRegistryInvoker =
      partyregistryproxy.client.invoker.ApiInvoker(partyregistryproxy.client.api.EnumsSerializers.all, blockingEc)
  }
  object PurposeProcessInvoker {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ActorSystem): PurposeProcessInvoker =
      purposeprocess.client.invoker.ApiInvoker(purposeprocess.client.api.EnumsSerializers.all, blockingEc)
  }

  object NotifierInvoker {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ActorSystem): NotifierInvoker =
      notifier.client.invoker.ApiInvoker(notifier.client.api.EnumsSerializers.all, blockingEc)
  }

  object TenantProcessInvoker {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ActorSystem): TenantProcessInvoker =
      tenantprocess.client.invoker.ApiInvoker(tenantprocess.client.api.EnumsSerializers.all, blockingEc)
  }
}
