package it.pagopa.interop.api.gateway

import akka.actor.ActorSystem
import it.pagopa.pdnd.interop.uservice._
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.invoker.Serializers
import it.pagopa.pdnd.interop.uservice.keymanagement.client.model.{Client, Key}
import org.json4s.DefaultFormats
import org.json4s.jackson.Serialization

package object service {
  type CatalogManagementInvoker           = catalogmanagement.client.invoker.ApiInvoker
  type PartyManagementInvoker             = partymanagement.client.invoker.ApiInvoker
  type AgreementManagementInvoker         = agreementmanagement.client.invoker.ApiInvoker
  type AuthorizationManagementInvoker     = keymanagement.client.invoker.ApiInvoker
  type AttributeRegistryManagementInvoker = attributeregistrymanagement.client.invoker.ApiInvoker
  type PurposeManagementInvoker           = purposemanagement.client.invoker.ApiInvoker

  type ManagementClient = Client

  object AgreementManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): AgreementManagementInvoker =
      agreementmanagement.client.invoker.ApiInvoker(agreementmanagement.client.api.EnumsSerializers.all)
  }

  object CatalogManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): CatalogManagementInvoker =
      catalogmanagement.client.invoker.ApiInvoker(catalogmanagement.client.api.EnumsSerializers.all)
  }

  object PartyManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): PartyManagementInvoker =
      partymanagement.client.invoker.ApiInvoker(partymanagement.client.api.EnumsSerializers.all)
  }

  object AuthorizationManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): AuthorizationManagementInvoker =
      keymanagement.client.invoker.ApiInvoker(keymanagement.client.api.EnumsSerializers.all)

    private def serializationFormats =
      DefaultFormats ++ Serializers.all ++ keymanagement.client.api.EnumsSerializers.all
    def serializeKey(key: Key) = Serialization.write(key)(serializationFormats)
  }

  object AttributeRegistryManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): AttributeRegistryManagementInvoker =
      attributeregistrymanagement.client.invoker.ApiInvoker(attributeregistrymanagement.client.api.EnumsSerializers.all)
  }

  object PurposeManagementInvoker {
    def apply()(implicit actorSystem: ActorSystem): PurposeManagementInvoker =
      purposemanagement.client.invoker.ApiInvoker(purposemanagement.client.api.EnumsSerializers.all)
  }

}
