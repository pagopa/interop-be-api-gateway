package it.pagopa.interop.api.gateway.common

import com.typesafe.config.{Config, ConfigFactory}

object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  def serverPort: Int = config.getInt("interop-api-gateway.port")

  def agreementManagementURL: String         = config.getString("services.agreement-management")
  def authorizationManagementURL: String     = config.getString("services.authorization-management")
  def catalogManagementURL: String           = config.getString("services.catalog-management")
  def partyManagementURL: String             = config.getString("services.party-management")
  def attributeRegistryManagementURL: String = config.getString("services.attribute-registry-management")

  def pdndIdIssuer: String = config.getString("interop-api-gateway.issuer")

  def vaultSecretsRootPath: String = config.getString("interop-api-gateway.vault-root-path").stripSuffix("/")

  def rsaPrivatePath =
    s"${vaultSecretsRootPath}/${config.getString("interop-api-gateway.rsa-private-path").stripPrefix("/")}"

  def ecPrivatePath =
    s"${vaultSecretsRootPath}/${config.getString("interop-api-gateway.ec-private-path").stripPrefix("/")}"

  def jwtAudience: Set[String] = Set.empty

}
