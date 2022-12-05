package it.pagopa.interop.apigateway

import akka.actor.ActorSystem
import it.pagopa.interop._

import scala.concurrent.ExecutionContextExecutor

package object service {
  type CatalogManagementInvoker           = catalogmanagement.client.invoker.ApiInvoker
  type AgreementManagementInvoker         = agreementmanagement.client.invoker.ApiInvoker
  type AuthorizationManagementInvoker     = authorizationmanagement.client.invoker.ApiInvoker
  type AttributeRegistryManagementInvoker = attributeregistrymanagement.client.invoker.ApiInvoker
  type PurposeManagementInvoker           = purposemanagement.client.invoker.ApiInvoker
  type NotifierInvoker                    = notifier.client.invoker.ApiInvoker
  type TenantProcessInvoker               = tenantprocess.client.invoker.ApiInvoker
  type TenantManagementInvoker            = tenantmanagement.client.invoker.ApiInvoker
  type PartyRegistryInvoker               = partyregistryproxy.client.invoker.ApiInvoker

  object AgreementManagementInvoker {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ActorSystem): AgreementManagementInvoker =
      agreementmanagement.client.invoker.ApiInvoker(agreementmanagement.client.api.EnumsSerializers.all, blockingEc)
  }

  object AuthorizationManagementInvoker {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ActorSystem): AuthorizationManagementInvoker =
      authorizationmanagement.client.invoker
        .ApiInvoker(authorizationmanagement.client.api.EnumsSerializers.all, blockingEc)
  }

  object CatalogManagementInvoker {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ActorSystem): CatalogManagementInvoker =
      catalogmanagement.client.invoker.ApiInvoker(catalogmanagement.client.api.EnumsSerializers.all, blockingEc)
  }

  object AttributeRegistryManagementInvoker {
    def apply(
      blockingEc: ExecutionContextExecutor
    )(implicit actorSystem: ActorSystem): AttributeRegistryManagementInvoker =
      attributeregistrymanagement.client.invoker
        .ApiInvoker(attributeregistrymanagement.client.api.EnumsSerializers.all, blockingEc)
  }

  object PartyRegistryInvoker     {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ActorSystem): PartyRegistryInvoker =
      partyregistryproxy.client.invoker.ApiInvoker(partyregistryproxy.client.api.EnumsSerializers.all, blockingEc)
  }
  object PurposeManagementInvoker {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ActorSystem): PurposeManagementInvoker =
      purposemanagement.client.invoker.ApiInvoker(purposemanagement.client.api.EnumsSerializers.all, blockingEc)
  }

  object NotifierInvoker {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ActorSystem): NotifierInvoker =
      notifier.client.invoker.ApiInvoker(notifier.client.api.EnumsSerializers.all, blockingEc)
  }

  object TenantProcessInvoker {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ActorSystem): TenantProcessInvoker =
      tenantprocess.client.invoker.ApiInvoker(tenantprocess.client.api.EnumsSerializers.all, blockingEc)
  }

  object TenantManagementInvoker {
    def apply(blockingEc: ExecutionContextExecutor)(implicit actorSystem: ActorSystem): TenantManagementInvoker =
      tenantmanagement.client.invoker.ApiInvoker(tenantmanagement.client.api.EnumsSerializers.all, blockingEc)
  }

}
