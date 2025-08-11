#!/usr/bin/env groovy

/**
 * Web-Compatible Report Management - Based on Working API Pipeline
 */

// =============================================================================
// ALLURE REPORT MANAGEMENT - SAME AS API PIPELINE
// =============================================================================

def prepareAllureHistory(String persistentHistoryDir) {
    sh """
        echo "📊 Preparing history files for Allure report"
        mkdir -p target/allure-results/history

        if [ -d '${persistentHistoryDir}' ] && [ "\$(ls -la '${persistentHistoryDir}' 2>/dev/null | wc -l)" -gt "3" ]; then
            echo "📋 Copying history from: ${persistentHistoryDir}"
            cp -f '${persistentHistoryDir}'/* target/allure-results/history/ 2>/dev/null || true
            echo "✅ History files copied"
        else
            echo "ℹ️ No existing history found"
        fi
    """
}

def generateAllureReport(String persistentHistoryDir, String allureCommandPath) {
    sh """
        export PATH="${allureCommandPath}:\$PATH"
        echo "🔄 Generating Allure report..."
        
        # 🔧 FIXED: Use same generation pattern as API pipeline
        allure generate target/allure-results -o allure-report --clean

        if [ -d "allure-report/history" ]; then
            echo "💾 Saving history to: ${persistentHistoryDir}"
            mkdir -p '${persistentHistoryDir}'
            cp -f allure-report/history/* '${persistentHistoryDir}'/ 2>/dev/null || true
            echo "✅ History saved"
        fi
    """
}

// =============================================================================
// STATISTICS COLLECTION - ALLURE BASED (SAME AS API)
// =============================================================================

def collectAllureStatistics() {
    def stats = [total: 0, passed: 0, failed: 0, broken: 0, skipped: 0, groupedStats: [:]]

    if (fileExists('allure-report/data/suites.json')) {
        def suitesJson = readJSON file: 'allure-report/data/suites.json'

        echo "📊 Processing Allure suites data..."

        // Process suites - same as API pipeline
        suitesJson.children.each { suite ->
            def suiteName = suite.name
            stats.groupedStats[suiteName] = [:]

            // Process tests in suite
            suite.children.each { testCase ->
                def testName = testCase.name

                // Initialize test stats if needed
                if (!stats.groupedStats[suiteName].containsKey(testName)) {
                    stats.groupedStats[suiteName][testName] = [total: 0, passed: 0, failed: 0, broken: 0, skipped: 0]
                }

                // Count the test
                stats.total++
                stats.groupedStats[suiteName][testName].total++

                // Update status counts
                switch (testCase.status) {
                    case 'passed':
                        stats.passed++
                        stats.groupedStats[suiteName][testName].passed++
                        break
                    case 'failed':
                        stats.failed++
                        stats.groupedStats[suiteName][testName].failed++
                        break
                    case 'broken':
                        stats.broken++
                        stats.groupedStats[suiteName][testName].broken++
                        break
                    case 'skipped':
                        stats.skipped++
                        stats.groupedStats[suiteName][testName].skipped++
                        break
                }
            }

            // Calculate success rates
            stats.groupedStats[suiteName].each { testName, testStats ->
                testStats.successRate = testStats.total > 0 ? (testStats.passed * 100 / testStats.total).intValue() : 100
            }
        }

        echo "📊 Web Test Statistics: Total: ${stats.total}, Passed: ${stats.passed}, Failed: ${stats.failed}, Broken: ${stats.broken}, Skipped: ${stats.skipped}"
    } else {
        echo "⚠️ Warning: suites.json file not found - test statistics will not be available"
    }

    return stats
}

// =============================================================================
// STATISTICS STORAGE - SAME AS API PIPELINE
// =============================================================================

def storeApiStatistics(def stats, def env) {
    env.LOCAL_TEST_COUNT = stats.total.toString()
    env.PASSED_COUNT = stats.passed.toString()
    env.FAILED_COUNT = stats.failed.toString()
    env.BROKEN_COUNT = stats.broken.toString()
    env.SKIPPED_COUNT = stats.skipped.toString()
    env.GROUPED_SUITE_STATS = groovy.json.JsonOutput.toJson(stats.groupedStats)

    echo "💾 Web test statistics stored in environment variables"
}

// =============================================================================
// ARTIFACT MANAGEMENT - SAME AS API PIPELINE
// =============================================================================

def archiveApiArtifacts() {
    archiveArtifacts artifacts: 'allure-report/**', allowEmptyArchive: true
    archiveArtifacts artifacts: '.env*', allowEmptyArchive: true
    archiveArtifacts artifacts: 'target/allure-results/**', allowEmptyArchive: true
    // Add web-specific artifacts
    archiveArtifacts artifacts: 'test-results/**', allowEmptyArchive: true
    archiveArtifacts artifacts: 'playwright-report/**', allowEmptyArchive: true
}

// =============================================================================
// FALLBACK METHODS FOR COMPATIBILITY
// =============================================================================

def collectPlaywrightStatistics() {
    // Fallback to Allure statistics for compatibility
    echo "📊 Using Allure statistics collection for web tests"
    return collectAllureStatistics()
}

def storeWebStatistics(def testStats, def env) {
    // Map web statistics to API format for consistency
    storeApiStatistics(testStats, env)
}

def archiveWebArtifacts() {
    // Use API artifact archiving which includes web artifacts
    archiveApiArtifacts()
}

return this
