#!/usr/bin/env groovy

/**
 * Report Management Utilities
 * Fixed version with proper path sanitization for Allure history
 */

// =============================================================================
// ALLURE REPORT MANAGEMENT (API TESTS) - FIXED VERSION
// =============================================================================

def prepareAllureHistory(String persistentHistoryDir) {
    // Sanitize the directory path to handle spaces and special characters
    def sanitizedDir = sanitizePath(persistentHistoryDir)

    sh """
        echo "📊 Preparing history files for Allure report"
        echo "🗂️ Original path: ${persistentHistoryDir}"
        echo "🔧 Sanitized path: ${sanitizedDir}"
        
        mkdir -p target/allure-results/history

        if [ -d "${sanitizedDir}" ] && [ "\$(ls -la '${sanitizedDir}' 2>/dev/null | wc -l)" -gt "3" ]; then
            echo "📋 Copying history from persistent location to target directory"
            cp -f '${sanitizedDir}'/* target/allure-results/history/ 2>/dev/null || true
            echo "✅ History files copied successfully"
        else
            echo "ℹ️ No existing history found at: ${sanitizedDir}"
        fi
    """
}

def generateAllureReport(String persistentHistoryDir, String allureCommandPath) {
    // Sanitize the directory path to handle spaces and special characters
    def sanitizedDir = sanitizePath(persistentHistoryDir)

    sh """
        export PATH="${allureCommandPath}:\$PATH"
        echo "🔄 Generating Allure report..."
        allure generate target/allure-results -o allure-report --clean

        if [ -d "allure-report/history" ]; then
            echo "💾 Saving history to persistent location: ${sanitizedDir}"
            mkdir -p '${sanitizedDir}'
            cp -f allure-report/history/* '${sanitizedDir}'/ 2>/dev/null || true
            echo "✅ History saved successfully"
        else
            echo "⚠️ No history directory found in generated report"
        fi
    """
}

/**
 * Sanitize path to handle spaces and special characters
 * This ensures proper shell command execution
 */
def sanitizePath(String originalPath) {
    // Extract the base directory and tag from the path
    def basePath = "/var/lib/jenkins/allure-history"

    // Extract tag from the path (everything after the last slash)
    def tag = originalPath.substring(originalPath.lastIndexOf('/') + 1)

    // Sanitize the tag name for filesystem safety
    def sanitizedTag = tag.toLowerCase()
            .replaceAll("@", "")
            .replaceAll("\\s+(and|or|not)\\s+", "-")
            .replaceAll("[^a-z0-9\\-_]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-\$", "")

    def sanitizedPath = "${basePath}/${sanitizedTag}"

    echo "🔧 Path sanitization: '${originalPath}' -> '${sanitizedPath}'"
    return sanitizedPath
}

// =============================================================================
// ALLURE REPORT URL GENERATION
// =============================================================================

def generateAllureReportUrl(String baseUrl, String serviceForUrl, String serviceNameForUrl, String buildPath) {
    def sanitizedTag = sanitizeServiceName(serviceForUrl)
    return "${baseUrl}/${sanitizedTag}/${serviceNameForUrl}/${buildPath}/index.html"
}

def sanitizeServiceName(String serviceName) {
    return serviceName.toLowerCase()
            .replaceAll("@", "")
            .replaceAll("\\s+(and|or|not)\\s+", "-")
            .replaceAll("[^a-z0-9\\-_]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-\$", "")
}

// =============================================================================
// PLAYWRIGHT REPORT MANAGEMENT (WEB TESTS)
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

// =============================================================================
// API TEST STATISTICS (ALLURE BASED)
// =============================================================================

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

// =============================================================================
// STATISTICS STORAGE
// =============================================================================

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
    // Mobile statistics storage implementation
    env.MOBILE_TEST_TOTAL = testStats.total.toString()
    env.MOBILE_TEST_PASSED = testStats.passed.toString()
    env.MOBILE_TEST_FAILED = testStats.failed.toString()
    env.MOBILE_TEST_SKIPPED = testStats.skipped.toString()

    echo "💾 Mobile test statistics stored in environment variables"
}

// =============================================================================
// ARTIFACT ARCHIVAL
// =============================================================================

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
    // Mobile artifact archival implementation
    archiveArtifacts artifacts: 'mobile-reports/**', allowEmptyArchive: true
    archiveArtifacts artifacts: 'screenshots/**', allowEmptyArchive: true
}

// =============================================================================
// REPORT URL GENERATION
// =============================================================================

def generateReportUrl(String baseUrl, String serviceForUrl, String serviceNameForUrl, String buildPath) {
    def sanitizedTag = sanitizeServiceName(serviceForUrl)
    return "${baseUrl}/${sanitizedTag}/${serviceNameForUrl}/${buildPath}/index.html"
}

// =============================================================================
// DEBUGGING HELPERS
// =============================================================================

def debugHistorySetup(String originalPath, String sanitizedPath) {
    sh """
        echo "🔍 Debug History Setup:"
        echo "  Original path: ${originalPath}"
        echo "  Sanitized path: ${sanitizedPath}"
        echo "  History dir exists: \$(test -d '${sanitizedPath}' && echo 'YES' || echo 'NO')"
        echo "  History dir contents:"
        ls -la '${sanitizedPath}' 2>/dev/null || echo "    (directory does not exist)"
        echo "  Target history dir:"
        ls -la target/allure-results/history/ 2>/dev/null || echo "    (directory does not exist)"
    """
}

return this
