#!groovy

node("executor") {
  checkout scm

  def commitHash  = sh(returnStdout: true, script: 'git rev-parse HEAD | cut -c-7').trim()
  def imageTag = "${env.BUILD_NUMBER}-${commitHash}"

  def e2eDevCypressCreds = usernamePassword(
    credentialsId: "e2e-dev-test-user-1-credentials",
    usernameVariable: "E2E_EMAIL",
    passwordVariable: "E2E_PASSWORD"
  )

  def e2eBlindReviewerCreds = usernamePassword(
    credentialsId: "e2e-blindreviewer-credentials",
    usernameVariable: "E2E_BLIND_REVIEWER_EMAIL",
    passwordVariable: "E2E_BLIND_REVIEWER_PASSWORD"
  )

  def e2eTrialUserCreds = usernamePassword(
    credentialsId: "e2e-trialuser-credentials",
    usernameVariable: "E2E_TRIAL_USER_EMAIL",
    passwordVariable: "E2E_TRIAL_USER_PASSWORD"
  )

  def blackfynnNexusCreds = usernamePassword(
    credentialsId: "blackfynn-nexus-ci-login",
    usernameVariable: "BLACKFYNN_NEXUS_USER",
    passwordVariable: "BLACKFYNN_NEXUS_PW"
  )

  if (env.BRANCH_NAME == "master") {
    properties([pipelineTriggers([cron("0 * * * *")])]) //every hour
  }

  def runTestsForTag = { String tagName ->
    echo "Branch is: ${env.BRANCH_NAME} for env: DEVELOPMENT"
    try {
        withEnv(['BF_ENVIRONMENT=DEVELOPMENT']) {
          withCredentials([e2eDevCypressCreds, e2eBlindReviewerCreds, e2eTrialUserCreds]) {
            sh "docker run -e E2E_EMAIL -e E2E_PASSWORD -e E2E_BLIND_REVIEWER_EMAIL -e E2E_BLIND_REVIEWER_PASSWORD -e BF_ENVIRONMENT -e E2E_TRIAL_USER_EMAIL -e E2E_TRIAL_USER_PASSWORD blackfynn/end-to-end-tests:${imageTag} -n ${tagName}"
          }
        }
    } catch (e) {
      slackSend(channel: "#end-to-end-tests", color: '#b20000', message: "FAILED: DEVELOPMENT ${tagName} Tests: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
      throw e
    }
    slackSend(channel: "#end-to-end-tests", color: '#006600', message: "SUCCESSFUL: DEVELOPMENT ${tagName} Tests: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
  }

  stage("Build") {
    withCredentials([blackfynnNexusCreds]) {
      sh "sbt -Dversion=$imageTag clean docker "
    }
  }

  stage("Test") {
    parallel(
      Processing_Dev: {runTestsForTag("Processing")},
      Streaming_Dev: {runTestsForTag("Streaming")},
      Trials_Dev: {runTestsForTag("Trials")},
      Packages_Dev: {runTestsForTag("Packages")},
      Authentication_Dev: {runTestsForTag("Authentication")},
      Upload_Dev: {runTestsForTag("Upload")},
      Discover_Dev: {runTestsForTag("Discover")},
      Metadata_Dev: {runTestsForTag("Metadata")},
    )
  }
}
