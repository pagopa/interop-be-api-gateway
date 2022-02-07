#!/bin/bash
SCRIPT_PATH="${BASH_SOURCE[0]}";
if ([ -h "${SCRIPT_PATH}" ]) then
  while([ -h "${SCRIPT_PATH}" ]) do SCRIPT_PATH=`readlink "${SCRIPT_PATH}"`; done
fi
pushd . > /dev/null
cd `dirname ${SCRIPT_PATH}` > /dev/null
SCRIPT_PATH=`pwd`;
popd  > /dev/null

NAMESPACE=$(source $SCRIPT_PATH/config; echo $name)
CHECK=$(kubectl get namespaces | grep $NAMESPACE | awk -F' ' '{ print $1 }')

if  ([ "${CHECK}" == "${NAMESPACE}" ]) then
  $SCRIPT_PATH/templater.sh $SCRIPT_PATH/deployment.yaml.template -s -f $SCRIPT_PATH/config > $SCRIPT_PATH/deployment.yaml

  kubectl delete -f $SCRIPT_PATH/deployment.yaml

  kubectl delete namespace $NAMESPACE && rm -rf $SCRIPT_PATH/deployment.yaml
fi