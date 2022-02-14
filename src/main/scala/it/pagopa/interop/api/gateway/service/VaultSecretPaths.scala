package it.pagopa.interop.api.gateway.service

import it.pagopa.interop.api.gateway.common.ApplicationConfiguration

object VaultSecretPaths {
  def extractKeyPath(algorithm: String, kind: String): String =
    s"${ApplicationConfiguration.vaultSecretsRootPath}/$algorithm/jwk/$kind"

  def extractPrivateKeysPath(algorithm: String): String = extractKeyPath(algorithm, "private")
  def extractPublicKeysPath(algorithm: String): String  = extractKeyPath(algorithm, "public")

}
