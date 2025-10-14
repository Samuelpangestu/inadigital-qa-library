#!/usr/bin/env groovy

// =============================================================================
// ENVIRONMENT DETECTION HELPER
// =============================================================================

class EnvironmentDetectionHelper {

    /**
     * Detect environment from Jenkins job name - matches Java logic
     */
    static String detectEnvironmentFromJobName(def env) {
        if (!isRunningInJenkins(env)) {
            return null
        }

        String jobName = env.JOB_NAME
        if (!jobName) {
            return null
        }

        String upperJobName = jobName.toUpperCase()

        if (upperJobName.contains("PROD")) {
            return "PROD"
        } else if (upperJobName.contains("STAGING") || upperJobName.contains("STG")) {
            return "STAGING"
        } else if (upperJobName.contains("DEV")) {
            return "DEV"
        }

        return null
    }

    /**
     * Check if running in Jenkins environment
     */
    static boolean isRunningInJenkins(def env) {
        return env.JOB_NAME && env.BUILD_NUMBER
    }

    /**
     * Get effective environment with priority logic
     * Priority: Jenkins job name > TARGET_ENV parameter > Default DEV
     */
    static String getEffectiveEnvironment(def env, def params) {
        // First priority: Jenkins job name detection
        String jenkinsEnv = detectEnvironmentFromJobName(env)
        if (jenkinsEnv) {
            return jenkinsEnv
        }

        // Second priority: TARGET_ENV parameter
        if (params.TARGET_ENV) {
            return params.TARGET_ENV.toUpperCase()
        }

        // Default fallback
        return "DEV"
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
// NOTIFICATION FORMATTER
// =============================================================================

class NotificationFormatter {

    static String getStatusEmoji(String status) {
        switch (status?.toUpperCase()) {
            case 'SUCCESS':
                return '✅'
            case 'FAILURE':
                return '❌'
            case 'UNSTABLE':
                return '⚠️'
            case 'ABORTED':
                return '🛑'
            default:
                return '❓'
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

// =============================================================================
// MESSAGE BUILDERS
// =============================================================================

class MessageHeaderBuilder {

    static String buildApiHeader() {
        return "*🚀 API TEST AUTOMATION REPORT*\\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }

    static String buildWebHeader() {
        return "*🌐 WEB TEST AUTOMATION REPORT*\\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }

    static String buildMobileHeader() {
        return "*📱 MOBILE TEST AUTOMATION REPORT*\\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }
}

class MessageBuildInfoBuilder {

    static String buildApiBuildInfo(String statusEmoji, String status, def env, def params, String commitId, String jobName, String currentTime, String executionTime) {
        // Use enhanced environment detection
        def effectiveEnvironment = EnvironmentDetectionHelper.getEffectiveEnvironment(env, params)

        return "${statusEmoji} *Build #${env.BUILD_NUMBER}* | ${status}\\n" +
                "📄 *Commit ID:* ${commitId}\\n" +
                "🌐 *Environment:* ${effectiveEnvironment}\\n" +
                "🏷️ *Tags:* @${env.EFFECTIVE_QA_SERVICE ?: params.QA_SERVICE}\\n" +
                "🔧 *Service:* ${params.QA_SERVICE_NAME ?: params.QA_SERVICE}\\n" +
                "📋 *Job:* ${jobName}\\n" +
                "🕒 *Time:* ${currentTime}\\n" +
                executionTime +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }

    static String buildWebBuildInfo(String statusEmoji, String status, def env, def params, String commitId, String jobName, String currentTime, String executionTime) {
        // Use enhanced environment detection
        def effectiveEnvironment = EnvironmentDetectionHelper.getEffectiveEnvironment(env, params)

        return "${statusEmoji} *Build #${env.BUILD_NUMBER}* | ${status}\\n" +
                "📄 *Commit ID:* ${commitId}\\n" +
                "🌐 *Environment:* ${effectiveEnvironment}\\n" +
                "🏷️ *Tags:* @${env.EFFECTIVE_QA_SERVICE ?: params.QA_SERVICE}\\n" +
                "🔧 *Browser:* ${params.BROWSER}\\n" +
                "👤 *Headless:* ${params.HEADLESS}\\n" +
                "📋 *Job:* ${jobName}\\n" +
                "🕒 *Time:* ${currentTime}\\n" +
                executionTime +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }

    static String buildMobileBuildInfo(String statusEmoji, String status, def env, def params, String commitId, String jobName, String currentTime, String executionTime) {
        // Use enhanced environment detection
        def effectiveEnvironment = EnvironmentDetectionHelper.getEffectiveEnvironment(env, params)

        return "${statusEmoji} *Build #${env.BUILD_NUMBER}* | ${status}\\n" +
                "📄 *Commit ID:* ${commitId}\\n" +
                "🌐 *Environment:* ${effectiveEnvironment}\\n" +
                "🏷️ *Tags:* @${env.EFFECTIVE_QA_SERVICE ?: params.QA_SERVICE}\\n" +
                "📱 *Device:* ${params.DEVICE_TYPE ?: 'Mobile'}\\n" +
                "📋 *Job:* ${jobName}\\n" +
                "🕒 *Time:* ${currentTime}\\n" +
                executionTime +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }
}

class MessageTestSummaryBuilder {

    static String buildApiTestSummary(Map testStats, int successRate, String progressBar) {
        return "📊 *TEST SUMMARY*\\n" +
                "${progressBar} ${successRate}%\\n\\n" +
                "✅ *Passed:* ${testStats.passed}\\n" +
                "❌ *Failed:* ${testStats.failed}\\n" +
                "💥 *Broken:* ${testStats.broken}\\n" +
                "⏭️ *Skipped:* ${testStats.skipped}\\n" +
                "📈 *Total:* ${testStats.total}\\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }

    static String buildWebTestSummary(Map testStats, int successRate, String progressBar) {
        return "📊 *TEST SUMMARY*\\n" +
                "${progressBar} ${successRate}%\\n\\n" +
                "✅ *Passed:* ${testStats.passed}\\n" +
                "❌ *Failed:* ${testStats.failed}\\n" +
                "🔄 *Flaky:* ${testStats.flaky}\\n" +
                "⏭️ *Skipped:* ${testStats.skipped}\\n" +
                "📈 *Total:* ${testStats.total}\\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }

    static String buildMobileTestSummary(Map testStats, int successRate, String progressBar) {
        return "📊 *TEST SUMMARY*\\n" +
                "${progressBar} ${successRate}%\\n\\n" +
                "✅ *Passed:* ${testStats.passed}\\n" +
                "❌ *Failed:* ${testStats.failed}\\n" +
                "⏭️ *Skipped:* ${testStats.skipped}\\n" +
                "📈 *Total:* ${testStats.total}\\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }
}

class MessageFooterBuilder {

    static String buildApiFooter(String reportUrl, def params) {
        return "📄 *View Test Reports:*\\n" +
                "[📊 ${params.QA_SERVICE} Allure Report](${reportUrl})\\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }

    static String buildWebFooter(String reportUrl) {
        return "📄 *View Test Reports:*\\n" +
                "[🎭 Playwright Report](${reportUrl})\\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }

    static String buildMobileFooter(String reportUrl) {
        return "📄 *View Test Report:*\\n" +
                "[📱 Mobile Test Report](${reportUrl})\\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }
}

// =============================================================================
// ENHANCED CONFIGURATION WITH ENVIRONMENT-SPECIFIC WEBHOOKS
// =============================================================================

final class NotificationConfig {
    // Environment-specific webhook mappings
    static final Map<String, Map<String, String>> ENVIRONMENT_SPECIFIC_WEBHOOKS = [
            'emeterai': [
                    'DEV'    : 'EMETERAI_DEV_WEBHOOK_URL',
                    'STAGING': 'EMETERAI_STAGING_WEBHOOK_URL',
                    'PROD'   : 'EMETERAI_PROD_WEBHOOK_URL'
            ],
            'digidoc' : [
                    'DEV'    : 'DIGIDOC_DEV_WEBHOOK_URL',
                    'STAGING': 'DIGIDOC_STAGING_WEBHOOK_URL',
                    'PROD'   : 'DIGIDOC_PROD_WEBHOOK_URL'
            ]
            // Add more services that need environment-specific webhooks
    ]

    // Standard service webhook mapping (for services without environment-specific needs)
    static final Map<String, String> SERVICE_WEBHOOK_MAPPING = [
            // INAGov Services
            'inagov'               : 'INAGOV_WEBHOOK_URL',
            'personal-data'        : 'INAGOV_WEBHOOK_URL',
            'aparatur'             : 'INAGOV_WEBHOOK_URL',
            'pembelajaran'         : 'INAGOV_WEBHOOK_URL',
            'dashbor'              : 'INAGOV_WEBHOOK_URL',

            // INAPas Services
            'inapas'               : 'INAPAS_WEBHOOK_URL',

            // INAKu Services
            'inaku'                : 'INAKU_WEBHOOK_URL',

            // MBG Services
            'mbg'                  : 'MBG_WEBHOOK_URL',

            // SBU Services (fallback for services not in environment-specific mapping)
            'sbu'                  : 'SBU_WEBHOOK_URL',
            'metel'                : 'METEL_WEBHOOK_URL',
            'digitrust'            : 'DIGITRUST_WEBHOOK_URL',
            'digidoc-dashboard-cmp': 'CMP_WEBHOOK_URL',

            // PeruriID Services
            'peruriid'             : 'PERURIID_WEBHOOK_URL',
            'wizard'               : 'PERURIID_WEBHOOK_URL',

            // Telkomsign Services
            'telkomsign'           : 'TELKOMSIGN_WEBHOOK_URL',

            // eMudhra Services
            'emudhra'              : 'EMUDHRA_WEBHOOK_URL',

            // Playground
            'playground'           : 'PLAYGROUND_WEBHOOK_URL',
    ]

    static final String DEFAULT_WEBHOOK = 'GENERAL_WEBHOOK_URL'
    static final String TIMEZONE = 'Asia/Jakarta'
    static final int PROGRESS_BAR_LENGTH = 10
}

// =============================================================================
// ENHANCED WEBHOOK MANAGER
// =============================================================================

class WebhookManager {

    /**
     * Get webhook URL with environment-aware mapping
     * Priority: Environment-specific webhook > Standard service webhook > Default webhook
     */
    def getWebhookUrl(String productName, String environment, def sh) {
        def normalizedTag = productName.toLowerCase().replaceAll('@', '')
        def normalizedEnv = environment?.toUpperCase() ?: 'DEV'

        // ✅ 1. Playground job should always use PLAYGROUND_WEBHOOK_URL from mapping
        if (normalizedTag.contains('playground')) {
            def playgroundKey = NotificationConfig.SERVICE_WEBHOOK_MAPPING['playground']
            def playgroundWebhook = sh(
                    script: "grep \"${playgroundKey}\" .env | cut -d= -f2-",
                    returnStdout: true
            ).trim()

            if (playgroundWebhook && !playgroundWebhook.isEmpty()) {
                return playgroundWebhook
            }
        }

        // ✅ 2. Try environment-specific webhook if available
        def envSpecificWebhook = getEnvironmentSpecificWebhook(normalizedTag, normalizedEnv)
        if (envSpecificWebhook) {
            def webhookUrl = sh(
                    script: "grep \"${envSpecificWebhook}\" .env | cut -d= -f2-",
                    returnStdout: true
            ).trim()

            if (webhookUrl && !webhookUrl.isEmpty()) {
                return webhookUrl
            }
        }

        // ✅ 3. Fallback to standard service webhook mapping
        def webhookKey = NotificationConfig.SERVICE_WEBHOOK_MAPPING.find { service, _ ->
            normalizedTag.contains(service)
        }?.value ?: NotificationConfig.DEFAULT_WEBHOOK

        return sh(
                script: "grep \"${webhookKey}\" .env | cut -d= -f2-",
                returnStdout: true
        ).trim()
    }

    /**
     * Find environment-specific webhook key for a service
     */
    private String getEnvironmentSpecificWebhook(String serviceName, String environment) {
        return NotificationConfig.ENVIRONMENT_SPECIFIC_WEBHOOKS.find { service, envMap ->
            serviceName.contains(service)
        }?.value?.get(environment)
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
// FEATURE STATISTICS HANDLER
// =============================================================================

class FeatureStatsHandler {

    def createFeatureStatsSection(def env, def readJSON) {
        if (!env.GROUPED_SUITE_STATS) return ""

        def featureStatsSection = "🔍 *FEATURE RESULTS*\\n"
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

            def featureSuccessRate = featureTotal > 0 ?
                    (featurePassed * 100 / featureTotal).intValue() : 100
            def featureEmoji = getFeatureEmoji(featureSuccessRate)

            featureStatsSection += "${featureEmoji} *${suiteName}:* ${featureSuccessRate}% (${featurePassed}/${featureTotal})\\n"
        }

        return featureStatsSection + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    }

    private String getFeatureEmoji(int successRate) {
        if (successRate == 100) return "✅"
        if (successRate >= 80) return "🟡"
        return "❌"
    }
}

// =============================================================================
// ENHANCED NOTIFICATION ORCHESTRATOR
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

    def sendApiTestNotification(String buildStatus, String reportUrl, String commitId, def env, def params) {
        def context = buildNotificationContext(buildStatus, commitId, env, params)
        def testStats = TestStatisticsCollector.getApiTestStatistics(env)
        def metrics = calculateMetrics(testStats)

        def message = buildApiMessage(context, testStats, metrics, reportUrl, params)

        // Pass environment to webhook manager
        def effectiveEnvironment = EnvironmentDetectionHelper.getEffectiveEnvironment(env, params)
        def webhookUrl = webhookManager.getWebhookUrl(context.productName, effectiveEnvironment, this.sh)

        this.echo "📡 Sending API notification for: ${context.productName} (${effectiveEnvironment})"
        this.echo "📡 Using webhook URL from: ${getWebhookKeyUsed(context.productName, effectiveEnvironment)}"

        webhookManager.sendMessage(webhookUrl, message, this.writeFile, this.sh)
    }

    def sendWebTestNotification(String buildStatus, String reportUrl, String commitId, def env, def params) {
        def context = buildNotificationContext(buildStatus, commitId, env, params)
        def testStats = TestStatisticsCollector.getWebTestStatistics(env)
        def metrics = calculateMetrics(testStats)

        def message = buildWebMessage(context, testStats, metrics, reportUrl, params)

        // Pass environment to webhook manager
        def effectiveEnvironment = EnvironmentDetectionHelper.getEffectiveEnvironment(env, params)
        def webhookUrl = webhookManager.getWebhookUrl(context.productName, effectiveEnvironment, this.sh)

        this.echo "📡 Sending Web notification for: ${context.productName} (${effectiveEnvironment})"

        webhookManager.sendMessage(webhookUrl, message, this.writeFile, this.sh)
    }

    def sendMobileTestNotification(String buildStatus, String reportUrl, String commitId, def env, def params) {
        def context = buildNotificationContext(buildStatus, commitId, env, params)
        def testStats = TestStatisticsCollector.getMobileTestStatistics(env)
        def metrics = calculateMetrics(testStats)

        def message = buildMobileMessage(context, testStats, metrics, reportUrl, params)

        // Pass environment to webhook manager
        def effectiveEnvironment = EnvironmentDetectionHelper.getEffectiveEnvironment(env, params)
        def webhookUrl = webhookManager.getWebhookUrl(context.productName, effectiveEnvironment, this.sh)

        this.echo "📡 Sending Mobile notification for: ${context.productName} (${effectiveEnvironment})"

        webhookManager.sendMessage(webhookUrl, message, this.writeFile, this.sh)
    }

    /**
     * Helper method to show which webhook key is being used (for debugging)
     */
    private String getWebhookKeyUsed(String productName, String environment) {
        def normalizedTag = productName.toLowerCase().replaceAll('@', '')
        def normalizedEnv = environment?.toUpperCase() ?: 'DEV'

        // Check environment-specific first
        def envSpecificKey = NotificationConfig.ENVIRONMENT_SPECIFIC_WEBHOOKS.find { service, envMap ->
            normalizedTag.contains(service)
        }?.value?.get(normalizedEnv)

        if (envSpecificKey) {
            return envSpecificKey
        }

        // Fallback to standard mapping
        return NotificationConfig.SERVICE_WEBHOOK_MAPPING.find { service, _ ->
            normalizedTag.contains(service)
        }?.value ?: NotificationConfig.DEFAULT_WEBHOOK
    }

    private Map buildNotificationContext(String buildStatus, String commitId, def env, def params) {
        return [
                jobName      : env.JOB_NAME.split('/')[-1],
                status       : buildStatus ?: 'SUCCESS',
                statusEmoji  : NotificationFormatter.getStatusEmoji(buildStatus ?: 'SUCCESS'),
                productName  : env.EFFECTIVE_QA_SERVICE ?: params.QA_SERVICE,
                currentTime  : NotificationFormatter.formatCurrentTime(),
                executionTime: NotificationFormatter.formatBuildDuration(this.currentBuild),
                commitId     : commitId,
                buildNumber  : env.BUILD_NUMBER
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
        def featureStats = featureStatsHandler.createFeatureStatsSection(this.env, this.readJSON)
        def footer = MessageFooterBuilder.buildWebFooter(reportUrl)

        return "${header}\\n\\n${buildInfo}\\n\\n${testSummary}\\n\\n${featureStats ? featureStats + '\\n\\n' : ''}${footer}"
    }

    private String buildMobileMessage(Map context, Map testStats, Map metrics, String reportUrl, def params) {
        def header = MessageHeaderBuilder.buildMobileHeader()
        def buildInfo = MessageBuildInfoBuilder.buildMobileBuildInfo(
                context.statusEmoji, context.status, this.env, params,
                context.commitId, context.jobName, context.currentTime, context.executionTime
        )
        def testSummary = MessageTestSummaryBuilder.buildMobileTestSummary(testStats, metrics.successRate, metrics.progressBar)
        def featureStats = featureStatsHandler.createFeatureStatsSection(this.env, this.readJSON)
        def footer = MessageFooterBuilder.buildMobileFooter(reportUrl)

        return "${header}\\n\\n${buildInfo}\\n\\n${testSummary}\\n\\n${featureStats ? featureStats + '\\n\\n' : ''}${footer}"
    }
}

// =============================================================================
// UPDATED PUBLIC API
// =============================================================================

// Enhanced notification methods that consider environment
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

return this
