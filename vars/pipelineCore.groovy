#!/usr/bin/env groovy

/**
 * Core Pipeline Logic for Test Automation
 * Centralized functions used across API, Web, and Mobile test pipelines
 */

// =============================================================================
// BUILD CONFIGURATION
// =============================================================================

def setupBuildMetadata(def env, def params, def commitId, String testType) {
    def timestamp = new Date().format("yyyyMMdd-HHmmss", TimeZone.getTimeZone('Asia/Jakarta'))
    def buildPath = "${timestamp}-${commitId}-build-${env.BUILD_NUMBER}"
    env.BUILD_PATH = buildPath

    // Set display names based on test type
    switch (testType.toLowerCase()) {
        case 'api':
            setupApiDisplayName(params, env)
            break
        case 'web':
            setupWebDisplayName(params, env)
            break
        case 'mobile':
            setupMobileDisplayName(params, env)
            break
    }

    return buildPath
}

def setupApiDisplayName(def params, def env) {
    def tagToUse = env.EFFECTIVE_QA_SERVICE ?: params.QA_SERVICE
    def displayTag = params.USE_CUSTOM_TAG ? "@${tagToUse}" : params.QA_SERVICE

    currentBuild.displayName = "#${currentBuild.number}: QA-API ${params.TARGET_ENV}: ${displayTag}"
    currentBuild.description = "Tag: @${tagToUse} | Environment: ${params.TARGET_ENV}"
}

def setupWebDisplayName(def params, def env) {
    currentBuild.displayName = "#${currentBuild.number}: Web Test ${params.TARGET_ENV}: ${params.QA_SERVICE}"
    currentBuild.description = "Playwright Web Automation - ${params.BROWSER} | Environment: ${params.TARGET_ENV}"
}

def setupMobileDisplayName(def params, def env) {
    currentBuild.displayName = "#${currentBuild.number}: Mobile Test ${params.TARGET_ENV}: ${params.QA_SERVICE}"
    currentBuild.description = "Mobile Automation - ${params.DEVICE_TYPE} | Environment: ${params.TARGET_ENV}"
}

// =============================================================================
// COMMON PIPELINE STAGES
// =============================================================================

def checkoutAndPrepare(String agentLabel) {
    node(agentLabel) {
        script {
            new DeployImage().cleanAll()
            checkout scm

            sh 'echo -n $(git rev-parse --short HEAD) > ./commit-id'
            def commitId = readFile('./commit-id').trim()

            stash(name: 'workspace', includes: '**, ./commit-id')
            return commitId
        }
    }
}

def setupEnvironmentCredentials(String envCredentialsId, Map additionalConfig = [:], String gcpCredentialsId = "qa-google-service-account-key") {
    // Copy main environment file
    withCredentials([file(credentialsId: envCredentialsId, variable: 'SECRET_FILE')]) {
        sh """
            echo "📄 Copying base .env file..."
            if [ -f "$SECRET_FILE" ]; then
                cp "$SECRET_FILE" "${WORKSPACE}/.env"
            else
                touch "${WORKSPACE}/.env"
            fi
            chmod 664 "${WORKSPACE}/.env"
        """

        // Append additional config values
        additionalConfig.each { key, value ->
            sh """
                echo "" >> "${WORKSPACE}/.env"
                echo "${key}=${value}" >> "${WORKSPACE}/.env"
            """
        }
    }

    // Optional: Google Service Account setup
    if (gcpCredentialsId) {
        withCredentials([file(credentialsId: gcpCredentialsId, variable: 'SERVICE_ACCOUNT_KEY')]) {
            sh """
                echo "🔑 Setting up Google Service Account key..."
                if [ -f "$SERVICE_ACCOUNT_KEY" ]; then
                    cp "$SERVICE_ACCOUNT_KEY" "${WORKSPACE}/key.json"
                    chmod 600 "${WORKSPACE}/key.json"
                else
                    echo "ERROR: SERVICE_ACCOUNT_KEY file not found"
                    exit 1
                fi
            """
        }
    }
}

def setupGoogleServiceAccount() {
    withCredentials([file(credentialsId: "qa-google-service-account-key", variable: 'SERVICE_ACCOUNT_KEY')]) {
        sh '''
            # Setup Google Service Account key
            if [ -f "$SERVICE_ACCOUNT_KEY" ]; then
                cat "$SERVICE_ACCOUNT_KEY" > key.json
                chmod 600 key.json
            else
                echo "ERROR: SERVICE_ACCOUNT_KEY file not found"
                exit 1
            fi
        '''
    }
}

// =============================================================================
// TEST EXECUTION HANDLERS
// =============================================================================

def executeApiTests(String tagToUse, String mavenPath, String allurePath) {
    env.PATH = "${mavenPath}:${allurePath}:${env.PATH}"

    echo "🚀 Running API tests with tag: @${tagToUse}"

    def persistentHistoryDir = "/var/lib/jenkins/allure-history/${tagToUse}"

    // Prepare Allure history
    allureUtils.prepareHistory(persistentHistoryDir)

    // Run tests
    sh "mvn test -Dcucumber.filter.tags=\"@${tagToUse}\""

    return persistentHistoryDir
}

def executeWebTests(def params, String tagToUse, String nodePath, String pnpmPath) {
    env.PATH = "${nodePath}:${pnpmPath}:${env.PATH}"
    env.ENV = params.TARGET_ENV.toUpperCase()
    env.HEADLESS = params.HEADLESS.toString()
    env.BROWSER = params.BROWSER

    echo "🚀 Running web tests with tag: @${tagToUse}"
    echo "🌐 Browser: ${params.BROWSER} | Headless: ${params.HEADLESS}"

    def browserConfig = buildBrowserConfig(params.BROWSER)
    def playwrightConfig = buildPlaywrightConfig(params.HEADLESS)

    try {
        timeout(time: 110, unit: 'MINUTES') {
            sh """
                npx playwright test ${browserConfig} ${playwrightConfig} --grep="@${tagToUse}"
            """
        }
    } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
        echo "⚠️ Tests timed out after 110 minutes: ${e.getMessage()}"
        currentBuild.result = 'UNSTABLE'
    } catch (Exception e) {
        echo "⚠️ Tests completed with failures: ${e.getMessage()}"
        currentBuild.result = 'UNSTABLE'
    }
}

// =============================================================================
// REPORT GENERATION
// =============================================================================

def generateTestReport(String testType, def additionalParams = [:]) {
    switch (testType.toLowerCase()) {
        case 'api':
            return generateApiReport(additionalParams)
        case 'web':
            return generateWebReport(additionalParams)
        case 'mobile':
            return generateMobileReport(additionalParams)
    }
}

def generateApiReport(def params) {
    def tagUsed = env.EFFECTIVE_QA_SERVICE ?: params.QA_SERVICE
    def persistentHistoryDir = "/var/lib/jenkins/allure-history/${tagUsed}"
    def allurePath = params.allurePath

    // Generate Allure report
    allureUtils.generateReport(persistentHistoryDir, allurePath)

    // Collect and store statistics
    def testStats = testUtils.collectStatistics()
    testUtils.storeStatistics(testStats, env)

    // Set build result based on test outcomes
    if (testStats.failed > 0 || testStats.broken > 0) {
        currentBuild.result = 'UNSTABLE'
        echo "⚠️ Setting build to UNSTABLE due to ${testStats.failed + testStats.broken} failed/broken tests"
    } else {
        echo "✅ All tests passed for tag: @${tagUsed}"
    }

    return testStats
}

def generateWebReport(def params) {
    echo "📊 Collecting web test statistics from JSON report"

    // Debug: Check if files exist
    sh "ls -la test-results/ || echo 'test-results directory not found'"
    sh "ls -la playwright-report/ || echo 'playwright-report directory not found'"

    // Collect test statistics
    def testStats = testUtils.collectWebTestStatisticsFromJSON()
    echo "Collected test statistics: ${testStats}"

    // Store statistics in environment variables
    testUtils.storeWebTestStatistics(testStats, env)

    // Set build result based on test outcomes
    if (testStats.failed > 0) {
        currentBuild.result = 'UNSTABLE'
        echo "⚠️ Setting build to UNSTABLE due to ${testStats.failed} failed tests"
    } else if (testStats.total == 0) {
        currentBuild.result = 'UNSTABLE'
        echo "⚠️ Setting build to UNSTABLE - no tests were executed"
    } else {
        echo "✅ All ${testStats.total} tests completed successfully"
        if (testStats.flaky > 0) {
            echo "ℹ️ Note: ${testStats.flaky} tests were flaky but ultimately passed"
        }
    }

    return testStats
}

def generateMobileReport(def params) {
    // Placeholder for mobile report generation
    echo "📱 Mobile report generation not implemented yet"
    return [total: 0, passed: 0, failed: 0, skipped: 0]
}

// =============================================================================
// NOTIFICATION HANDLING
// =============================================================================

def sendTestNotification(String testType, String buildResult, String reportUrl, String commitId, def env, def params) {
    try {
        switch (testType.toLowerCase()) {
            case 'api':
                notificationUtils.sendGoogleChatNotification(buildResult, reportUrl, commitId, env, params)
                break
            case 'web':
                notificationUtils.sendWebTestNotification(buildResult, reportUrl, commitId, env, params)
                break
            case 'mobile':
                notificationUtils.sendMobileTestNotification(buildResult, reportUrl, commitId, env, params)
                break
        }
    } catch (Exception e) {
        echo "❌ Failed to send ${testType} notification: ${e.getMessage()}"
    }
}

// =============================================================================
// HELPER FUNCTIONS FOR WEB TESTS
// =============================================================================

def buildBrowserConfig(String browser) {
    switch (browser) {
        case 'chromium':
            return '--project=chromium'
        case 'firefox':
            return '--project=firefox'
        case 'webkit':
            return '--project=webkit'
        case 'all':
            return ''
        default:
            return '--project=chromium'
    }
}

def buildPlaywrightConfig(boolean headless) {
    def config = []

    if (headless == false) {
        config.add('--headed')
    }

    config.add('--trace=on-first-retry')
    return config.join(' ')
}

// =============================================================================
// BUILD RESULT MANAGEMENT
// =============================================================================

def setBuildResult(def testStats, String testType = 'generic') {
    if (testStats.failed > 0) {
        currentBuild.result = 'UNSTABLE'
        echo "⚠️ Setting build to UNSTABLE due to ${testStats.failed} failed tests"
        return 'UNSTABLE'
    } else if (testStats.total == 0) {
        currentBuild.result = 'UNSTABLE'
        echo "⚠️ Setting build to UNSTABLE - no tests were executed"
        return 'UNSTABLE'
    } else {
        echo "✅ All ${testStats.total} tests completed successfully"
        return 'SUCCESS'
    }
}

return this
