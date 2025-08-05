// =============================================================================
// COMMON UTILITIES
// =============================================================================

def getStatusEmoji(String status) {
    switch (status) {
        case "SUCCESS":
            return "🟢"
        case "UNSTABLE":
            return "🟠"
        case "FAILURE":
            return "🔴"
        default:
            return "⚪"
    }
}

def calculateSuccessRate(def testStats) {
    return testStats.total > 0 ? (testStats.passed * 100 / testStats.total).intValue() : 100
}

def createProgressBar(int successRate) {
    def progressBar = ""
    def barLength = 10
    def filledBars = (successRate * barLength / 100).intValue()

    for (int i = 0; i < barLength; i++) {
        progressBar += (i < filledBars) ? "🟩" : "⬜"
    }

    return progressBar
}

def getBuildDuration(def env) {
    try {
        def buildDuration = currentBuild.durationString.replace(" and counting", "")
        return "⏱️ *Duration:* ${buildDuration}\\n"
    } catch (Exception e) {
        return ""
    }
}

def getWebhookUrl(String productName) {
    def tag = productName.toLowerCase().replaceAll('@', '')

    // SBU services
    def sbuServices = ['sbu', 'digidoc', 'emeterai', 'meterai', 'metel']
    if (sbuServices.any { tag.contains(it) }) {
        return sh(script: 'grep "SBU_WEBHOOK_URL" .env | cut -d= -f2-', returnStdout: true).trim()
    }

    // Peruri ID services
    def peruriServices = ['peruriid', 'wizard']
    if (peruriServices.any { tag.contains(it) }) {
        return sh(script: 'grep "PERURIID_WEBHOOK_URL" .env | cut -d= -f2-', returnStdout: true).trim()
    }

    // Default
    return sh(script: 'grep "GENERAL_WEBHOOK_URL" .env | cut -d= -f2-', returnStdout: true).trim()
}

def sendChatMessage(String webhookUrl, String message) {
    def jsonPayload = """{"text": "${message}"}"""
    writeFile file: 'chat_payload.json', text: jsonPayload

    sh """
        curl -s -X POST \\
             -H 'Content-Type: application/json' \\
             --data @chat_payload.json \\
             '${webhookUrl}'
    """
}

// =============================================================================
// API TEST NOTIFICATIONS (Allure-based)
// =============================================================================

def sendGoogleChatNotification(
        String buildStatus,
        String reportUrl,
        String commitId,
        def env,
        def params
) {
    def jobName = env.JOB_NAME.split('/')[-1]
    def status = buildStatus ?: 'SUCCESS'
    def statusEmoji = getStatusEmoji(status)
    def productName = env.EFFECTIVE_QA_SERVICE ?: params.QA_SERVICE

    def webhookUrl = getWebhookUrl(productName)
    echo "Using webhook for API tests: ${productName}"

    def testStats = getApiTestStatistics(env)
    def successRate = calculateSuccessRate(testStats)
    def progressBar = createProgressBar(successRate)
    def executionTime = getBuildDuration(env)
    def currentTime = new Date().format("yyyy-MM-dd HH:mm:ss", TimeZone.getTimeZone('Asia/Jakarta'))

    def formattedMessage = createApiNotificationMessage(
            statusEmoji, status, env, params, commitId, jobName,
            currentTime, executionTime, testStats, successRate,
            progressBar, reportUrl
    )

    sendChatMessage(webhookUrl, formattedMessage)
}

def getApiTestStatistics(def env) {
    return [
            total  : env.LOCAL_TEST_COUNT ? env.LOCAL_TEST_COUNT.toInteger() : 0,
            passed : env.PASSED_COUNT ? env.PASSED_COUNT.toInteger() : 0,
            failed : env.FAILED_COUNT ? env.FAILED_COUNT.toInteger() : 0,
            broken : env.BROKEN_COUNT ? env.BROKEN_COUNT.toInteger() : 0,
            skipped: env.SKIPPED_COUNT ? env.SKIPPED_COUNT.toInteger() : 0
    ]
}

def createApiNotificationMessage(
        statusEmoji, status, env, params, commitId, jobName,
        currentTime, executionTime, testStats, successRate,
        progressBar, reportUrl
) {
    def header = createApiNotificationHeader()
    def buildInfo = createApiBuildInfo(statusEmoji, status, env, params, commitId, jobName, currentTime, executionTime)
    def testSummary = createApiTestSummary(testStats, successRate, progressBar)
    def featureStats = createFeatureStatsSection(env)
    def footer = createApiFooter(reportUrl, params)

    return "${header}\\n\\n${buildInfo}\\n\\n${testSummary}\\n\\n${featureStats ? featureStats + '\\n\\n' : ''}${footer}"
}

def createApiNotificationHeader() {
    return  "*API*\\n" +
            "*PERURI TEST AUTOMATION REPORT*\\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

def createApiBuildInfo(statusEmoji, status, env, params, commitId, jobName, currentTime, executionTime) {
    return "${statusEmoji} *Build #${env.BUILD_NUMBER}* | ${status}\\n" +
            "🔄 *Commit ID:* ${commitId}\\n" +
            "🌐 *Environment:* ${params.TARGET_ENV}\\n" +
            "🏷️ *Tags:* @${env.EFFECTIVE_QA_SERVICE ?: params.QA_SERVICE}\\n" +
            "🔧 *Service:* ${params.QA_SERVICE_NAME ?: params.QA_SERVICE}\\n" +
            "📋 *Job:* ${jobName}\\n" +
            "🕒 *Time:* ${currentTime}\\n" +
            executionTime +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

def createApiTestSummary(testStats, successRate, progressBar) {
    return "📊 *TEST RESULTS* | ${successRate}% Success\\n" +
            "${progressBar}\\n\\n" +
            "🔢 *Total Tests:* ${testStats.total}\\n" +
            "✅ *Passed:* ${testStats.passed}\\n" +
            "❌ *Failed:* ${testStats.failed}\\n" +
            "⚠️ *Broken:* ${testStats.broken}\\n" +
            "⏭️ *Skipped:* ${testStats.skipped}\\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

def createFeatureStatsSection(def env) {
    if (!env.GROUPED_SUITE_STATS) return ""

    def featureStatsSection = "📑 *FEATURE RESULTS*\\n"
    def groupedStats = readJSON text: env.GROUPED_SUITE_STATS

    groupedStats.keySet().sort().each { suiteName ->
        def tests = groupedStats[suiteName]
        def featureTotal = 0
        def featurePassed = 0

        tests.keySet().each { testName ->
            def stats = tests[testName]
            featureTotal += stats.total
            featurePassed += stats.passed
        }

        def featureSuccessRate = featureTotal > 0 ? (featurePassed * 100 / featureTotal).intValue() : 100
        def featureEmoji = getFeatureEmoji(featureSuccessRate)

        featureStatsSection += "${featureEmoji} *${suiteName}:* ${featureSuccessRate}% (${featurePassed}/${featureTotal})\\n"
    }

    return featureStatsSection + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

def getFeatureEmoji(int successRate) {
    if (successRate == 100) return "✅"
    if (successRate >= 80) return "🟡"
    return "❌"
}

def createApiFooter(reportUrl, params) {
    return "📄 *View Full Report:*\\n" +
            "[${params.QA_SERVICE_NAME ?: params.QA_SERVICE} Allure Report](${reportUrl})\\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

// =============================================================================
// WEB TEST NOTIFICATIONS (Playwright-based)
// =============================================================================

def sendWebTestNotification(
        String buildStatus,
        String playwrightReportUrl,
        String commitId,
        def env,
        def params
) {
    def jobName = env.JOB_NAME.split('/')[-1]
    def status = buildStatus ?: 'SUCCESS'
    def statusEmoji = getStatusEmoji(status)
    def productName = env.EFFECTIVE_QA_SERVICE ?: params.QA_SERVICE

    def webhookUrl = getWebhookUrl(productName)
    echo "Using webhook for web tests: ${productName}"

    def testStats = getWebTestStatistics(env)
    def successRate = calculateSuccessRate(testStats)
    def progressBar = createProgressBar(successRate)
    def executionTime = getBuildDuration(env)
    def currentTime = new Date().format("yyyy-MM-dd HH:mm:ss", TimeZone.getTimeZone('Asia/Jakarta'))

    def formattedMessage = createWebNotificationMessage(
            statusEmoji, status, env, params, commitId, jobName,
            currentTime, executionTime, testStats, successRate,
            progressBar, playwrightReportUrl
    )

    sendChatMessage(webhookUrl, formattedMessage)
}

def getWebTestStatistics(def env) {
    return [
            total  : env.TEST_TOTAL ? env.TEST_TOTAL.toInteger() : 0,
            passed : env.TEST_PASSED ? env.TEST_PASSED.toInteger() : 0,
            failed : env.TEST_FAILED ? env.TEST_FAILED.toInteger() : 0,
            skipped: env.TEST_SKIPPED ? env.TEST_SKIPPED.toInteger() : 0,
            flaky  : env.TEST_FLAKY ? env.TEST_FLAKY.toInteger() : 0
    ]
}

def createWebNotificationMessage(
        statusEmoji, status, env, params, commitId, jobName,
        currentTime, executionTime, testStats, successRate,
        progressBar, playwrightReportUrl
) {
    def header = createWebNotificationHeader()
    def buildInfo = createWebBuildInfo(statusEmoji, status, env, params, commitId, jobName, currentTime, executionTime)
    def testSummary = createWebTestSummary(testStats, successRate, progressBar)
    def footer = createWebFooter(playwrightReportUrl)

    return "${header}\\n\\n${buildInfo}\\n\\n${testSummary}\\n\\n${footer}"
}

def createWebNotificationHeader() {
    return  "*WEB*\\n" +
            "*PERURI TEST AUTOMATION REPORT*\\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

def createWebBuildInfo(statusEmoji, status, env, params, commitId, jobName, currentTime, executionTime) {
    return "${statusEmoji} *Build #${env.BUILD_NUMBER}* | ${status}\\n" +
            "🔄 *Commit ID:* ${commitId}\\n" +
            "🌐 *Environment:* ${params.TARGET_ENV}\\n" +
            "🏷️ *Service:* ${params.QA_SERVICE}\\n" +
            "🔧 *Browser:* ${params.BROWSER}\\n" +
            "👤 *Headless:* ${params.HEADLESS}\\n" +
            "📋 *Job:* ${jobName}\\n" +
            "🕒 *Time:* ${currentTime}\\n" +
            executionTime +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

def createWebTestSummary(testStats, successRate, progressBar) {
    def summary = "📊 *WEB TEST RESULTS* | ${successRate}% Success\\n" +
            "${progressBar}\\n\\n" +
            "🔢 *Total Tests:* ${testStats.total}\\n" +
            "✅ *Passed:* ${testStats.passed}\\n" +
            "❌ *Failed:* ${testStats.failed}\\n" +
            "⏭️ *Skipped:* ${testStats.skipped}\\n"

    // Only add flaky count if there are flaky tests
    if (testStats.flaky > 0) {
        summary += "🔀 *Flaky:* ${testStats.flaky}\\n"
    }

    summary += "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    return summary
}

def createWebFooter(playwrightReportUrl) {
    return "📄 *View Test Reports:*\\n" +
            "[🎭 Playwright Report](${playwrightReportUrl})\\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

return this
