# Interoperability - API gateway


generated package: it.pagopa.interop.apigateway

source  package: it.pagopa.interop.apigateway.impl

repoName: interop-be-api-gateway

build.sbt: 
packagePrefix := name.value
  .replaceFirst("interop-", "interop.")
  .replaceFirst("be-", "")
  .replaceAll("-", "")