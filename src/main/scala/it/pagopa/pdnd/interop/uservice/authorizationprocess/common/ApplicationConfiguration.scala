package it.pagopa.pdnd.interop.uservice.authorizationprocess.common

import com.typesafe.config.{Config, ConfigFactory}

object ApplicationConfiguration {
  lazy val config: Config = ConfigFactory.load()

  def serverPort: Int = {
    config.getInt("uservice-authorization-process.port")
  }

  def getAgreementManagementURL: String     = config.getString("services.agreement-management")
  def getAuthorizationManagementURL: String = config.getString("services.authorization-management")
  def getCatalogManagementURL: String       = config.getString("services.catalog-management")
  def getPartyManagementURL: String         = config.getString("services.party-management")

  def jwtAudience: Set[String] = Set.empty

}
