#!/usr/bin/env groovy

/**
 * Enhanced Notification Utilities - Refactored for Better Readability
 * Centralized notification management for API, Web, and Mobile test results
 *
 * @author Test Automation Team
 * @version 2.0
 */

// =============================================================================
// CONFIGURATION CONSTANTS
// =============================================================================

final class NotificationConfig {
    static final Map<String, String> SERVICE_WEBHOOK_MAPPING = [
            // INAGov Services
            'inagov': 'INAGOV_WEBHOOK_URL',
            'personal-data': 'INAGOV_WEBHOOK_URL',
            'aparatur': 'INAGOV_WEBHOOK_URL',
            'pembelajaran': 'INAGOV_WEBHOOK_URL',
            'dashbor': 'INAGOV_WEBHOOK_URL',

            // INAPas Services
            'inapas': 'INAPAS_WEBHOOK_URL',

            // INAKu Services
            'inaku': 'INAKU_WEBHOOK_URL',

            // MBG Services
            'mbg': 'MBG_WEBHOOK_URL',

            // SBU Services
            'sbu': 'SBU_WEBHOOK_URL',
            'emeterai': 'SBU_WEBHOOK_URL',
            'metel': 'METEL_WEBHOOK_URL',
            'digitrust': 'DIGITRUST_WEBHOOK_URL',
            'digidoc-dashboard-cmp': 'CMP_WEBHOOK_URL',

            // PeruriID Services
            'peruriid': 'PERURIID_WEBHOOK_URL',
            'wizard': 'PERURIID_WEBHOOK_URL',

            // Telkomsign Services
            'telkomsign': 'TELKOMSIGN_WEBHOOK_URL',

            // eMudhra Services
            'emudhra': 'EMUDHRA_WEBHOOK_URL',
    ]

    static final String DEFAULT_WEBHOOK = 'GENERAL_WEBHOOK_URL'
    static final String TIMEZONE = 'Asia/Jakarta'
    static final int PROGRESS_BAR_LENGTH = 10
}

// =============================================================================
// CORE UTILITY FUNCTIONS
// =============================================================================

class NotificationFormatter {

    static String getStatusEmoji(String status) {
        switch (status?.toUpperCase()) {
            case "SUCCESS": return "🟢"
            case "UNSTABLE": return "🟠"
            case "FAILURE": return "🔴"
            default: return "⚪"
        }
    }

    static int calculateSuccessRate(Map testStats) {
        def total = testStats.total ?: 0
        def passed = testStats.passed ?: 0
        return total > 0 ? (passed * 100 / total).intValue() : 100
    }

    static String createProgressBar(int successRate) {
        def progressBar = new StringBuilder()
        def filledBars = (successRate * NotificationConfig.PROGRESS_BAR_LENGTH / 100).intValue()

        for (int i = 0; i < NotificationConfig.PROGRESS_BAR_LENGTH; i++) {
            progressBar.append(i < filledBars ? "🟩" : "⬜")
        }

        return progressBar.toString()
    }

    static String formatCurrentTime() {
        return new Date().format("yyyy-MM-dd HH:mm:ss", TimeZone.getTimeZone(NotificationConfig.TIMEZONE))
    }

    static String formatBuildDuration(def currentBuild) {
        try {
            def buildDuration = currentBuild.durationString.replace(" and counting", "")
            return "⏱️ *Duration:* ${buildDuration}\\n"
        } catch (Exception e) {
            return ""
        }
    }
}

class WebhookManager {

    def getWebhookUrl(String productName, def sh) {
        def normalizedTag = productName.toLowerCase().replaceAll('@', '')

        def webhookKey = NotificationConfig.SERVICE_WEBHOOK_MAPPING.find { service, _ ->
            normalizedTag.contains(service)
        }?.value ?: NotificationConfig.DEFAULT_WEBHOOK

        return sh(script: "grep \"${webhookKey}\" .env | cut -d= -f2-", returnStdout: true).trim()
    }

    def sendMessage(String webhookUrl, String message, def writeFile, def sh) {
        def jsonPayload = """{"text": "${message}"}"""
        writeFile(file: 'chat_payload.json', text: jsonPayload)

        sh(script: """
            curl -s -X POST \\
                 -H 'Content-Type: application/json' \\
                 --data @chat_payload.json \\
                 '${webhookUrl}'
        """)
    }
}

// =============================================================================
// TEST STATISTICS COLLECTORS
// =============================================================================

class TestStatisticsCollector {

    static Map getApiTestStatistics(def env) {
        return [
                total  : env.LOCAL_TEST_COUNT?.toInteger() ?: 0,
                passed : env.PASSED_COUNT?.toInteger() ?: 0,
                failed : env.FAILED_COUNT?.toInteger() ?: 0,
                broken : env.BROKEN_COUNT?.toInteger() ?: 0,
                skipped: env.SKIPPED_COUNT?.toInteger() ?: 0
        ]
    }

    static Map getWebTestStatistics(def env) {
        return [
                total  : env.TEST_TOTAL?.toInteger() ?: 0,
                passed : env.TEST_PASSED?.toInteger() ?: 0,
                failed : env.TEST_FAILED?.toInteger() ?: 0,
                skipped: env.TEST_SKIPPED?.toInteger() ?: 0,
                flaky  : env.TEST_FLAKY?.toInteger() ?: 0
        ]
    }

    static Map getMobileTestStatistics(def env) {
        return [
                total  : env.MOBILE_TEST_TOTAL?.toInteger() ?: 0,
                passed : env.MOBILE_TEST_PASSED?.toInteger() ?: 0,
                failed : env.MOBILE_TEST_FAILED?.toInteger() ?: 0,
                skipped: env.MOBILE_TEST_SKIPPED?.toInteger() ?: 0
        ]
    }
}

// =============================================================================
// MESSAGE BUILDERS - HEADERS
// =============================================================================

class MessageHeaderBuilder {

    static String buildApiHeader() {
        return "*🚀 API TEST AUTOMATION REPORT*\\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }

    static String buildWebHeader() {
        return "*🌐 WEB TEST AUTOMATION REPORT*\\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }

    static String buildMobileHeader() {
        return "*📱 MOBILE TEST AUTOMATION REPORT*\\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }
}

class MessageBuildInfoBuilder {

    static String buildApiBuildInfo(String statusEmoji, String status, def env, def params, String commitId, String jobName, String currentTime, String executionTime) {
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

    static String buildWebBuildInfo(String statusEmoji, String status, def env, def params, String commitId, String jobName, String currentTime, String executionTime) {
        return "${statusEmoji} *Build #${env.BUILD_NUMBER}* | ${status}\\n" +
                "🔄 *Commit ID:* ${commitId}\\n" +
                "🌐 *Environment:* ${params.TARGET_ENV}\\n" +
                "🏷️ *Tags:* @${env.EFFECTIVE_QA_SERVICE ?: params.QA_SERVICE}\\n" +
                "🔧 *Browser:* ${params.BROWSER}\\n" +
                "👤 *Headless:* ${params.HEADLESS}\\n" +
                "📋 *Job:* ${jobName}\\n" +
                "🕒 *Time:* ${currentTime}\\n" +
                executionTime +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }

    static String buildMobileBuildInfo(String statusEmoji, String status, def env, def params, String commitId, String jobName, String currentTime, String executionTime) {
        return "${statusEmoji} *Build #${env.BUILD_NUMBER}* | ${status}\\n" +
                "🔄 *Commit ID:* ${commitId}\\n" +
                "🌐 *Environment:* ${params.TARGET_ENV}\\n" +
                "🏷️ *Tags:* @${env.EFFECTIVE_QA_SERVICE ?: params.QA_SERVICE}\\n" +
                "📱 *Device:* ${params.DEVICE_TYPE ?: 'Default'}\\n" +
                "📋 *Job:* ${jobName}\\n" +
                "🕒 *Time:* ${currentTime}\\n" +
                executionTime +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }
}

class MessageTestSummaryBuilder {

    static String buildApiTestSummary(Map testStats, int successRate, String progressBar) {
        return "📊 *TEST RESULTS* | ${successRate}% Success\\n" +
                "${progressBar}\\n\\n" +
                "🔢 *Total Tests:* ${testStats.total}\\n" +
                "✅ *Passed:* ${testStats.passed}\\n" +
                "❌ *Failed:* ${testStats.failed}\\n" +
                "⚠️ *Broken:* ${testStats.broken}\\n" +
                "⏭️ *Skipped:* ${testStats.skipped}\\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }

    static String buildWebTestSummary(Map testStats, int successRate, String progressBar) {
        def summary = "📊 *WEB TEST RESULTS* | ${successRate}% Success\\n" +
                "${progressBar}\\n\\n" +
                "🔢 *Total Tests:* ${testStats.total}\\n" +
                "✅ *Passed:* ${testStats.passed}\\n" +
                "❌ *Failed:* ${testStats.failed}\\n" +
                "⏭️ *Skipped:* ${testStats.skipped}\\n"

        if (testStats.flaky > 0) {
            summary += "🔀 *Flaky:* ${testStats.flaky}\\n"
        }

        summary += "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        return summary
    }

    static String buildMobileTestSummary(Map testStats, int successRate, String progressBar) {
        return "📊 *MOBILE TEST RESULTS* | ${successRate}% Success\\n" +
                "${progressBar}\\n\\n" +
                "🔢 *Total Tests:* ${testStats.total}\\n" +
                "✅ *Passed:* ${testStats.passed}\\n" +
                "❌ *Failed:* ${testStats.failed}\\n" +
                "⏭️ *Skipped:* ${testStats.skipped}\\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }
}

class MessageFooterBuilder {

    static String buildApiFooter(String reportUrl, def params) {
        return "📄 *View Full Report:*\\n" +
                "[${params.QA_SERVICE_NAME ?: params.QA_SERVICE} Allure Report](${reportUrl})\\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }

    static String buildWebFooter(String reportUrl) {
        return "📄 *View Test Reports:*\\n" +
                "[🎭 Playwright Report](${reportUrl})\\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }

    static String buildMobileFooter(String reportUrl) {
        return "📄 *View Test Report:*\\n" +
                "[📱 Mobile Test Report](${reportUrl})\\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }
}

// =============================================================================
// FEATURE STATISTICS HANDLER
// =============================================================================

class FeatureStatsHandler {

    def createFeatureStatsSection(def env, def readJSON) {
        if (!env.GROUPED_SUITE_STATS) return ""

        def featureStatsSection = "📑 *FEATURE RESULTS*\\n"
        def groupedStats = readJSON(text: env.GROUPED_SUITE_STATS)

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

    private String getFeatureEmoji(int successRate) {
        if (successRate == 100) return "✅"
        if (successRate >= 80) return "🟡"
        return "❌"
    }
}

// =============================================================================
// NOTIFICATION ORCHESTRATOR
// =============================================================================

class NotificationOrchestrator {

    private WebhookManager webhookManager
    private FeatureStatsHandler featureStatsHandler
    private def currentBuild
    private def env
    private def echo
    private def readJSON
    private def sh
    private def writeFile

    NotificationOrchestrator(def currentBuild, def env, def echo, def readJSON, def sh, def writeFile) {
        this.webhookManager = new WebhookManager()
        this.featureStatsHandler = new FeatureStatsHandler()
        this.currentBuild = currentBuild
        this.env = env
        this.echo = echo
        this.readJSON = readJSON
        this.sh = sh
        this.writeFile = writeFile
    }

    def sendTestNotification(String testType, String buildStatus, String reportUrl, String commitId, def env, def params) {
        switch(testType.toLowerCase()) {
            case 'api':
                sendApiTestNotification(buildStatus, reportUrl, commitId, env, params)
                break
            case 'web':
                sendWebTestNotification(buildStatus, reportUrl, commitId, env, params)
                break
            case 'mobile':
                sendMobileTestNotification(buildStatus, reportUrl, commitId, env, params)
                break
            default:
                this.echo "⚠️ Unknown test type: ${testType}"
        }
    }

    def sendApiTestNotification(String buildStatus, String reportUrl, String commitId, def env, def params) {
        def context = buildNotificationContext(buildStatus, commitId, env, params)
        def testStats = TestStatisticsCollector.getApiTestStatistics(env)
        def metrics = calculateMetrics(testStats)

        def message = buildApiMessage(context, testStats, metrics, reportUrl, params)

        def webhookUrl = webhookManager.getWebhookUrl(context.productName, this.sh)
        this.echo "📡 Sending API notification for: ${context.productName}"

        webhookManager.sendMessage(webhookUrl, message, this.writeFile, this.sh)
    }

    def sendWebTestNotification(String buildStatus, String reportUrl, String commitId, def env, def params) {
        def context = buildNotificationContext(buildStatus, commitId, env, params)
        def testStats = TestStatisticsCollector.getWebTestStatistics(env)
        def metrics = calculateMetrics(testStats)

        def message = buildWebMessage(context, testStats, metrics, reportUrl, params)

        def webhookUrl = webhookManager.getWebhookUrl(context.productName, this.sh)
        this.echo "📡 Sending Web notification for: ${context.productName}"

        webhookManager.sendMessage(webhookUrl, message, this.writeFile, this.sh)
    }

    def sendMobileTestNotification(String buildStatus, String reportUrl, String commitId, def env, def params) {
        def context = buildNotificationContext(buildStatus, commitId, env, params)
        def testStats = TestStatisticsCollector.getMobileTestStatistics(env)
        def metrics = calculateMetrics(testStats)

        def message = buildMobileMessage(context, testStats, metrics, reportUrl, params)

        def webhookUrl = webhookManager.getWebhookUrl(context.productName, this.sh)
        this.echo "📡 Sending Mobile notification for: ${context.productName}"

        webhookManager.sendMessage(webhookUrl, message, this.writeFile, this.sh)
    }

    private Map buildNotificationContext(String buildStatus, String commitId, def env, def params) {
        return [
                jobName: env.JOB_NAME.split('/')[-1],
                status: buildStatus ?: 'SUCCESS',
                statusEmoji: NotificationFormatter.getStatusEmoji(buildStatus ?: 'SUCCESS'),
                productName: env.EFFECTIVE_QA_SERVICE ?: params.QA_SERVICE,
                currentTime: NotificationFormatter.formatCurrentTime(),
                executionTime: NotificationFormatter.formatBuildDuration(this.currentBuild),
                commitId: commitId,
                buildNumber: env.BUILD_NUMBER
        ]
    }

    private Map calculateMetrics(Map testStats) {
        def successRate = NotificationFormatter.calculateSuccessRate(testStats)
        return [
                successRate: successRate,
                progressBar: NotificationFormatter.createProgressBar(successRate)
        ]
    }

    private String buildApiMessage(Map context, Map testStats, Map metrics, String reportUrl, def params) {
        def header = MessageHeaderBuilder.buildApiHeader()
        def buildInfo = MessageBuildInfoBuilder.buildApiBuildInfo(
                context.statusEmoji, context.status, this.env, params,
                context.commitId, context.jobName, context.currentTime, context.executionTime
        )
        def testSummary = MessageTestSummaryBuilder.buildApiTestSummary(testStats, metrics.successRate, metrics.progressBar)
        def featureStats = featureStatsHandler.createFeatureStatsSection(this.env, this.readJSON)
        def footer = MessageFooterBuilder.buildApiFooter(reportUrl, params)

        return "${header}\\n\\n${buildInfo}\\n\\n${testSummary}\\n\\n${featureStats ? featureStats + '\\n\\n' : ''}${footer}"
    }

    private String buildWebMessage(Map context, Map testStats, Map metrics, String reportUrl, def params) {
        def header = MessageHeaderBuilder.buildWebHeader()
        def buildInfo = MessageBuildInfoBuilder.buildWebBuildInfo(
                context.statusEmoji, context.status, this.env, params,
                context.commitId, context.jobName, context.currentTime, context.executionTime
        )
        def testSummary = MessageTestSummaryBuilder.buildWebTestSummary(testStats, metrics.successRate, metrics.progressBar)
        def footer = MessageFooterBuilder.buildWebFooter(reportUrl)

        return "${header}\\n\\n${buildInfo}\\n\\n${testSummary}\\n\\n${footer}"
    }

    private String buildMobileMessage(Map context, Map testStats, Map metrics, String reportUrl, def params) {
        def header = MessageHeaderBuilder.buildMobileHeader()
        def buildInfo = MessageBuildInfoBuilder.buildMobileBuildInfo(
                context.statusEmoji, context.status, this.env, params,
                context.commitId, context.jobName, context.currentTime, context.executionTime
        )
        def testSummary = MessageTestSummaryBuilder.buildMobileTestSummary(testStats, metrics.successRate, metrics.progressBar)
        def footer = MessageFooterBuilder.buildMobileFooter(reportUrl)

        return "${header}\\n\\n${buildInfo}\\n\\n${testSummary}\\n\\n${footer}"
    }
}

// =============================================================================
// PUBLIC API - BACKWARD COMPATIBILITY
// =============================================================================

// Unified notification dispatcher
def sendTestNotification(String testType, String buildStatus, String reportUrl, String commitId, def env, def params) {
    def orchestrator = new NotificationOrchestrator(
            currentBuild,
            env,
            { msg -> echo(msg) },
            { args -> readJSON(args) },
            { args -> sh(args) },
            { args -> writeFile(args) }
    )
    orchestrator.sendTestNotification(testType, buildStatus, reportUrl, commitId, env, params)
}

// Specific notification methods
def sendApiTestNotification(String buildStatus, String reportUrl, String commitId, def env, def params) {
    def orchestrator = new NotificationOrchestrator(
            currentBuild,
            env,
            { msg -> echo(msg) },
            { args -> readJSON(args) },
            { args -> sh(args) },
            { args -> writeFile(args) }
    )
    orchestrator.sendApiTestNotification(buildStatus, reportUrl, commitId, env, params)
}

def sendWebTestNotification(String buildStatus, String reportUrl, String commitId, def env, def params) {
    def orchestrator = new NotificationOrchestrator(
            currentBuild,
            env,
            { msg -> echo(msg) },
            { args -> readJSON(args) },
            { args -> sh(args) },
            { args -> writeFile(args) }
    )
    orchestrator.sendWebTestNotification(buildStatus, reportUrl, commitId, env, params)
}

def sendMobileTestNotification(String buildStatus, String reportUrl, String commitId, def env, def params) {
    def orchestrator = new NotificationOrchestrator(
            currentBuild,
            env,
            { msg -> echo(msg) },
            { args -> readJSON(args) },
            { args -> sh(args) },
            { args -> writeFile(args) }
    )
    orchestrator.sendMobileTestNotification(buildStatus, reportUrl, commitId, env, params)
}

// Legacy method alias
def sendGoogleChatNotification(String buildStatus, String reportUrl, String commitId, def env, def params) {
    def orchestrator = new NotificationOrchestrator(
            currentBuild,
            env,
            { msg -> echo(msg) },
            { args -> readJSON(args) },
            { args -> sh(args) },
            { args -> writeFile(args) }
    )
    orchestrator.sendApiTestNotification(buildStatus, reportUrl, commitId, env, params)
}

return this