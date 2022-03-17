void sbtAction(String task) {
    sh '''
        echo "
        realm=Sonatype Nexus Repository Manager
        host=${NEXUS}
        user=${NEXUS_CREDENTIALS_USR}
        password=${NEXUS_CREDENTIALS_PSW}" > ~/.sbt/.credentials
        '''
    sh "sbt -Dsbt.log.noformat=true ${task}"
} 

void updateGithubCommit(String status) {
  def token = '${GITHUB_PAT_PSW}'
  sh """
    curl --silent --show-error \
      "https://api.github.com/repos/pagopa/${REPO_NAME}/statuses/${GIT_COMMIT}" \
      --header "Content-Type: application/json" \
      --header "Authorization: token ${token}" \
      --request POST \
      --data "{\\"state\\": \\"${status}\\",\\"context\\": \\"Jenkins Continuous Integration\\", \\"description\\": \\"Build ${BUILD_DISPLAY_NAME}\\"}" &> /dev/null
  """
}

void ecrLogin() {
  withCredentials([usernamePassword(credentialsId: 'ecr-rw', usernameVariable: 'AWS_ACCESS_KEY_ID', passwordVariable: 'AWS_SECRET_ACCESS_KEY')]) {
    sh '''
    aws ecr get-login-password --region eu-central-1 | docker login --username AWS --password-stdin $DOCKER_REPO
    '''
  }
}

pipeline {
  agent { label 'sbt-template' }
    environment {
    NEXUS = "${env.NEXUS}"
    DOCKER_REPO = "${env.DOCKER_REPO}"
    MAVEN_REPO = "${env.MAVEN_REPO}"
    GITHUB_PAT = credentials('github-pat')
    NEXUS_CREDENTIALS = credentials('pdnd-nexus')
    ECR_RW = credentials('ecr-rw')
    // GIT_URL has the shape git@github.com:pagopa/REPO_NAME.git so we extract from it
    REPO_NAME="""${sh(returnStdout:true, script: 'echo ${GIT_URL} | sed "s_git@github\\.com:pagopa/\\(.*\\)\\.git_\\1_g"')}""".trim()
  }
  stages {
    stage('Test') {
      steps {
        container('sbt-container') {
          updateGithubCommit 'pending'
          sbtAction 'test'
        }
      }
    }
    stage('Publish Client on Nexus and Docker Image on ECR') {
      when {
        anyOf {
          branch pattern: "[0-9]+\\.[0-9]+\\.x", comparator: "REGEXP"
          buildingTag()
        }
      }
      steps {
        container('sbt-container') {
          script {
            ecrLogin()
            sbtAction 'docker:publish "project client" publish'
          }
        }
      }
    }
  }
  post {
    success { 
      updateGithubCommit 'success'
    }
    failure { 
      updateGithubCommit 'failure'
    }
    aborted { 
      updateGithubCommit 'error'
    }
  }
}