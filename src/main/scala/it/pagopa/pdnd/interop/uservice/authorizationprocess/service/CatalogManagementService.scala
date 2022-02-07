package it.pagopa.pdnd.interop.uservice.authorizationprocess.service

import it.pagopa.pdnd.interop.uservice.authorizationprocess.model.{
  Descriptor => ApiDescriptor,
  EService => ApiEService,
  EServiceDescriptorState => ApiEServiceDescriptorState,
  Organization => ApiOrganization
}
import it.pagopa.pdnd.interop.uservice.catalogmanagement.client.model.{
  EService,
  EServiceDescriptor,
  EServiceDescriptorState
}

import java.util.UUID
import scala.concurrent.Future

trait CatalogManagementService {

  def getEService(bearerToken: String, eServiceId: UUID): Future[EService]
}

object CatalogManagementService {

  def eServiceToApi(
    eService: EService,
    provider: ApiOrganization,
    activeDescriptor: Option[EServiceDescriptor]
  ): ApiEService =
    ApiEService(eService.id, eService.name, provider, activeDescriptor.map(descriptorToApi))

  def descriptorToApi(descriptor: EServiceDescriptor): ApiDescriptor =
    ApiDescriptor(descriptor.id, descriptorStateToApi(descriptor.state), descriptor.version)

  def getActiveDescriptor(eService: EService): Option[EServiceDescriptor] =
    eService.descriptors.find(_.state == EServiceDescriptorState.PUBLISHED)

  def descriptorStateToApi(state: EServiceDescriptorState): ApiEServiceDescriptorState =
    state match {
      case EServiceDescriptorState.DRAFT      => ApiEServiceDescriptorState.DRAFT
      case EServiceDescriptorState.PUBLISHED  => ApiEServiceDescriptorState.PUBLISHED
      case EServiceDescriptorState.DEPRECATED => ApiEServiceDescriptorState.DEPRECATED
      case EServiceDescriptorState.SUSPENDED  => ApiEServiceDescriptorState.SUSPENDED
      case EServiceDescriptorState.ARCHIVED   => ApiEServiceDescriptorState.ARCHIVED
    }

}
