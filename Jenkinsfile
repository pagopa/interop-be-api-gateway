//common helper for invoking SBT tasks
void sbtAction(String task) {
  echo "Executing ${task} on SBT"
  sh '''
      echo "
      realm=Sonatype Nexus Repository Manager
      host=${NEXUS}
      user=${NEXUS_CREDENTIALS_USR}
      password=${NEXUS_CREDENTIALS_PSW}" > ~/.sbt/.credentials
     '''

  sh "sbt -Dsbt.log.noformat=true ${task}"
}

pipeline {

  agent none

  stages {
    stage('Test and Publish') {
      agent { label 'sbt-template' }
      environment {
        NEXUS = "${env.NEXUS}"
        NEXUS_CREDENTIALS = credentials('pdnd-nexus')
        DOCKER_REPO = "${env.DOCKER_REPO}"
        MAVEN_REPO = "${env.MAVEN_REPO}"
        ECR_RW = credentials('ecr-rw')
      }
      steps {
        container('sbt-container') {
          script {
            withCredentials([usernamePassword(credentialsId: 'ecr-rw', usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
              sh '''
              aws ecr get-login-password --region eu-central-1 | docker login --username AWS --password-stdin $DOCKER_REPO
              '''
            }
            sbtAction 'test docker:publish "project client" publish'
          }
        }
      }
    }

  }
}