#!/usr/bin/env groovy

/**
 * Report Management Utilities - Simple Fix for Allure History
 */

// =============================================================================
// ALLURE REPORT MANAGEMENT - SIMPLE FIX
// =============================================================================

def prepareAllureHistory(String persistentHistoryDir) {
    // Auto-sanitize the path internally
    def cleanDir = sanitizeHistoryPath(persistentHistoryDir)

    sh """
        echo "📊 Preparing history files for Allure report"
        mkdir -p target/allure-results/history

        if [ -d '${cleanDir}' ] && [ "\$(ls -la '${cleanDir}' 2>/dev/null | wc -l)" -gt "3" ]; then
            echo "📋 Copying history from: ${cleanDir}"
            cp -f '${cleanDir}'/* target/allure-results/history/ 2>/dev/null || true
            echo "✅ History files copied"
        else
            echo "ℹ️ No existing history found"
        fi
    """
}

def generateAllureReport(String persistentHistoryDir, String allureCommandPath) {
    // Auto-sanitize the path internally
    def cleanDir = sanitizeHistoryPath(persistentHistoryDir)

    sh """
        export PATH="${allureCommandPath}:\$PATH"
        echo "🔄 Generating Allure report..."
        allure generate target/allure-results -o allure-report --clean

        if [ -d "allure-report/history" ]; then
            echo "💾 Saving history to: ${cleanDir}"
            mkdir -p '${cleanDir}'
            cp -f allure-report/history/* '${cleanDir}'/ 2>/dev/null || true
            echo "✅ History saved"
        fi
    """
}

/**
 * Simple path sanitization - handles spaces and special characters
 */
private def sanitizeHistoryPath(String originalPath) {
    // Extract tag from path
    def tag = originalPath.substring(originalPath.lastIndexOf('/') + 1)

    // Clean the tag
    def cleanTag = tag.toLowerCase()
            .replaceAll('@', '')
            .replaceAll('\\s+', '-')
            .replaceAll('[^a-z0-9\\-_]', '-')
            .replaceAll('-+', '-')
            .replaceAll('^-|-$', '')

    // Return clean path
    return "/var/lib/jenkins/allure-history/${cleanTag}"
}

// =============================================================================
// REST OF THE FILE REMAINS THE SAME
// =============================================================================

def collectPlaywrightStatistics() {
    def jsonPath = "test-results/result.json"

    if (!fileExists(jsonPath)) {
        echo "📄 JSON report not found at: ${jsonPath}, falling back to HTML parsing"
        return collectPlaywrightStatisticsFromHTML()
    }

    try {
        def jsonContent = readFile(jsonPath)
        def results = readJSON text: jsonContent

        echo "📊 Processing Playwright JSON report..."

        def stats = [
                total: 0,
                passed: 0,
                failed: 0,
                skipped: 0,
                flaky: 0
        ]

        // Try stats first (Playwright v1.53+ format)
        if (results.stats) {
            stats.passed = (results.stats.expected ?: 0) as Integer
            stats.failed = (results.stats.unexpected ?: 0) as Integer
            stats.skipped = (results.stats.skipped ?: 0) as Integer
            stats.flaky = (results.stats.flaky ?: 0) as Integer
            stats.total = stats.passed + stats.failed + stats.skipped

            echo "📈 Stats found - expected: ${results.stats.expected}, unexpected: ${results.stats.unexpected}, skipped: ${results.stats.skipped}, flaky: ${results.stats.flaky}"
        }

        // If stats didn't give us results, try counting suites
        if (stats.total == 0 && results.suites) {
            echo "📊 Stats empty, counting from suites..."
            results.suites.each { suite ->
                countSuiteStats(suite, stats)
            }
        }

        echo "📊 Web Test Statistics from JSON: Total=${stats.total}, Passed=${stats.passed}, Failed=${stats.failed}, Skipped=${stats.skipped}, Flaky=${stats.flaky}"
        return stats

    } catch (Exception e) {
        echo "❌ Error parsing JSON report: ${e.getMessage()}"
        return collectPlaywrightStatisticsFromHTML()
    }
}

def countSuiteStats(def suite, def stats) {
    // Count nested suites
    if (suite.suites) {
        suite.suites.each { nestedSuite ->
            countSuiteStats(nestedSuite, stats)
        }
    }

    // Count direct specs
    if (suite.specs) {
        suite.specs.each { spec ->
            stats.total++
            if (spec.tests && spec.tests.size() > 0) {
                def testResult = spec.tests[0]
                switch (testResult.status) {
                    case 'expected':
                        stats.passed++
                        break
                    case 'unexpected':
                        stats.failed++
                        break
                    case 'flaky':
                        stats.flaky++
                        stats.passed++
                        break
                    case 'skipped':
                        stats.skipped++
                        break
                }
            }
        }
    }
}

def collectPlaywrightStatisticsFromHTML() {
    def htmlPath = "playwright-report/index.html"

    if (!fileExists(htmlPath)) {
        echo "❌ HTML report not found at: ${htmlPath}"
        return [total: 0, passed: 0, failed: 0, skipped: 0, flaky: 0]
    }

    try {
        def htmlContent = readFile(htmlPath)

        // Look for test result patterns in HTML
        def passedMatch = (htmlContent =~ /(\d+)\s+passed/)
        def failedMatch = (htmlContent =~ /(\d+)\s+failed/)
        def skippedMatch = (htmlContent =~ /(\d+)\s+skipped/)
        def flakyMatch = (htmlContent =~ /(\d+)\s+flaky/)

        def passed = passedMatch ? passedMatch[0][1] as Integer : 0
        def failed = failedMatch ? failedMatch[0][1] as Integer : 0
        def skipped = skippedMatch ? skippedMatch[0][1] as Integer : 0
        def flaky = flakyMatch ? flakyMatch[0][1] as Integer : 0
        def total = passed + failed + skipped

        echo "📊 Web Test Statistics from HTML: Total=${total}, Passed=${passed}, Failed=${failed}, Skipped=${skipped}, Flaky=${flaky}"

        return [
                total: total,
                passed: passed,
                failed: failed,
                skipped: skipped,
                flaky: flaky
        ]

    } catch (Exception e) {
        echo "❌ Error parsing HTML report: ${e.getMessage()}"
        return [total: 0, passed: 0, failed: 0, skipped: 0, flaky: 0]
    }
}

def collectAllureStatistics() {
    def stats = [total: 0, passed: 0, failed: 0, broken: 0, skipped: 0, groupedStats: [:]]

    if (fileExists('allure-report/data/suites.json')) {
        def suitesJson = readJSON file: 'allure-report/data/suites.json'

        echo "📊 Processing Allure suites data..."

        // Process suites
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

        echo "📊 API Test Statistics: Total: ${stats.total}, Passed: ${stats.passed}, Failed: ${stats.failed}, Broken: ${stats.broken}, Skipped: ${stats.skipped}"
    } else {
        echo "⚠️ Warning: suites.json file not found - API test statistics will not be available"
    }

    return stats
}

def storeApiStatistics(def stats, def env) {
    env.LOCAL_TEST_COUNT = stats.total.toString()
    env.PASSED_COUNT = stats.passed.toString()
    env.FAILED_COUNT = stats.failed.toString()
    env.BROKEN_COUNT = stats.broken.toString()
    env.SKIPPED_COUNT = stats.skipped.toString()
    env.GROUPED_SUITE_STATS = groovy.json.JsonOutput.toJson(stats.groupedStats)

    echo "💾 API test statistics stored in environment variables"
}

def storeWebStatistics(def testStats, def env) {
    env.TEST_TOTAL = testStats.total.toString()
    env.TEST_PASSED = testStats.passed.toString()
    env.TEST_FAILED = testStats.failed.toString()
    env.TEST_SKIPPED = testStats.skipped.toString()
    env.TEST_FLAKY = testStats.flaky ? testStats.flaky.toString() : "0"

    echo "💾 Web test statistics stored in environment variables"
    echo "📊 TEST_TOTAL=${env.TEST_TOTAL}, TEST_PASSED=${env.TEST_PASSED}, TEST_FAILED=${env.TEST_FAILED}, TEST_SKIPPED=${env.TEST_SKIPPED}, TEST_FLAKY=${env.TEST_FLAKY}"
}

def storeMobileStatistics(def testStats, def env) {
    env.MOBILE_TEST_TOTAL = testStats.total.toString()
    env.MOBILE_TEST_PASSED = testStats.passed.toString()
    env.MOBILE_TEST_FAILED = testStats.failed.toString()
    env.MOBILE_TEST_SKIPPED = testStats.skipped.toString()

    echo "💾 Mobile test statistics stored in environment variables"
}

def archiveApiArtifacts() {
    archiveArtifacts artifacts: 'allure-report/**', allowEmptyArchive: true
    archiveArtifacts artifacts: '.env*', allowEmptyArchive: true
    archiveArtifacts artifacts: 'target/allure-results/**', allowEmptyArchive: true
}

def archiveWebArtifacts() {
    archiveArtifacts artifacts: 'playwright-report/**', allowEmptyArchive: true
    archiveArtifacts artifacts: 'test-results/**', allowEmptyArchive: true
}

def archiveWebArtifactsOnFailure() {
    archiveArtifacts artifacts: 'test-results/**/*.png', allowEmptyArchive: true
    archiveArtifacts artifacts: 'test-results/**/*.webm', allowEmptyArchive: true
    archiveArtifacts artifacts: 'test-results/**/*.zip', allowEmptyArchive: true
}

def archiveMobileArtifacts() {
    archiveArtifacts artifacts: 'mobile-reports/**', allowEmptyArchive: true
    archiveArtifacts artifacts: 'screenshots/**', allowEmptyArchive: true
}

def generateReportUrl(String baseUrl, String serviceForUrl, String serviceNameForUrl, String buildPath) {
    def sanitizedTag = serviceForUrl.toLowerCase()
            .replaceAll("@", "")
            .replaceAll("\\s+(and|or|not)\\s+", "-")
            .replaceAll("[^a-z0-9\\-_]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-\$", "")

    return "${baseUrl}/${sanitizedTag}/${serviceNameForUrl}/${buildPath}/index.html"
}

return this
