package it.pagopa.interop.api.gateway.common

import com.typesafe.config.{Config, ConfigFactory}

object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  def serverPort: Int = config.getInt("interop-api-gateway.port")

  def getAgreementManagementURL: String     = config.getString("services.agreement-management")
  def getAuthorizationManagementURL: String = config.getString("services.authorization-management")
  def getCatalogManagementURL: String       = config.getString("services.catalog-management")
  def getPartyManagementURL: String         = config.getString("services.party-management")

  def getPdndIdIssuer: String = config.getString("interop-api-gateway.issuer")

  def getVaultSecretsRootPath: String = config.getString("interop-api-gateway.vault-root-path")

  def jwtAudience: Set[String] = Set.empty

}
