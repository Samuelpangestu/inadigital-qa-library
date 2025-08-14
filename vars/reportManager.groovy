#!/usr/bin/env groovy

/**
 * Report Management Utilities
 * Enhanced for Web Allure Integration with Playwright
 */

// =============================================================================
// ALLURE REPORT MANAGEMENT (API & WEB TESTS)
// =============================================================================

def prepareAllureHistory(String persistentHistoryDir) {
    sh """
        echo "📊 Preparing history files for Allure report"
        mkdir -p allure-results/history

        if [ -d "${persistentHistoryDir}" ] && [ "\$(ls -la ${persistentHistoryDir} 2>/dev/null | wc -l)" -gt "3" ]; then
            echo "📋 Copying history from persistent location to allure-results directory"
            cp -f ${persistentHistoryDir}/* allure-results/history/ 2>/dev/null || true
        fi
    """
}

def generateAllureReport(String persistentHistoryDir, String allureCommandPath) {
    sh """
        export PATH="${allureCommandPath}:$PATH"
        echo "🔄 Generating Allure report..."
        allure generate allure-results -o allure-report --clean

        if [ -d "allure-report/history" ]; then
            echo "💾 Saving history to persistent location"
            mkdir -p ${persistentHistoryDir}
            cp -f allure-report/history/* ${persistentHistoryDir}/ 2>/dev/null || true
        fi
    """
}

// =============================================================================
// WEB ALLURE REPORT MANAGEMENT (PLAYWRIGHT + ALLURE)
// =============================================================================

def prepareWebAllureResults() {
    sh '''
        echo "📊 Preparing Allure results directory for web tests"
        mkdir -p allure-results
        
        # Copy Playwright artifacts to Allure format if they exist
        if [ -d "test-results" ]; then
            # Copy screenshots
            find test-results -name "*.png" -exec cp {} allure-results/ \\; 2>/dev/null || true
            # Copy videos  
            find test-results -name "*.webm" -exec cp {} allure-results/ \\; 2>/dev/null || true
            # Copy traces
            find test-results -name "*.zip" -exec cp {} allure-results/ \\; 2>/dev/null || true
            
            echo "📎 Copied Playwright artifacts to Allure results"
        fi
    '''
}

def setupWebAllureEnvironment(def params, def env) {
    def envContent = """
Environment=${params.TARGET_ENV}
Browser=${params.BROWSER}
Headless=${params.HEADLESS}
Tag=${env.EFFECTIVE_QA_SERVICE}
Build=${env.BUILD_NUMBER}
Branch=${env.GIT_BRANCH ?: 'main'}
"""

    writeFile file: 'allure-results/environment.properties', text: envContent
    echo "🌍 Set Allure environment properties for web tests"
}

def addWebAllureCategories() {
    def categoriesContent = '''[
  {
    "name": "Failed Tests",
    "matchedStatuses": ["failed"],
    "messageRegex": ".*"
  },
  {
    "name": "Broken Tests", 
    "matchedStatuses": ["broken"],
    "messageRegex": ".*"
  },
  {
    "name": "Skipped Tests",
    "matchedStatuses": ["skipped"]
  },
  {
    "name": "Flaky Tests",
    "matchedStatuses": ["failed"],
    "messageRegex": ".*retry.*"
  },
  {
    "name": "UI Issues",
    "matchedStatuses": ["failed"],
    "messageRegex": ".*(timeout|element not found|selector).*"
  }
]'''

    writeFile file: 'allure-results/categories.json', text: categoriesContent
    echo "📂 Added Allure categories for web tests"
}

// =============================================================================
// COMBINED WEB REPORT GENERATION
// =============================================================================

def generateWebAllureReport(String persistentHistoryDir, String allureCommandPath, def params, def env) {
    // Setup Allure environment for web tests
    prepareWebAllureResults()
    setupWebAllureEnvironment(params, env)
    addWebAllureCategories()

    // Generate combined report (Playwright + Allure)
    prepareAllureHistory(persistentHistoryDir)
    generateAllureReport(persistentHistoryDir, allureCommandPath)

    echo "✅ Web Allure report generated with Playwright artifacts"
}

// =============================================================================
// PLAYWRIGHT REPORT MANAGEMENT
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
    // Archive both Playwright and Allure reports
    archiveArtifacts artifacts: 'allure-report/**', allowEmptyArchive: true
    archiveArtifacts artifacts: 'playwright-report/**', allowEmptyArchive: true
    archiveArtifacts artifacts: 'test-results/**', allowEmptyArchive: true
    archiveArtifacts artifacts: 'allure-results/**', allowEmptyArchive: true
}

def archiveWebArtifactsOnFailure() {
    archiveArtifacts artifacts: 'test-results/**/*.png', allowEmptyArchive: true
    archiveArtifacts artifacts: 'test-results/**/*.webm', allowEmptyArchive: true
    archiveArtifacts artifacts: 'test-results/**/*.zip', allowEmptyArchive: true
    archiveArtifacts artifacts: 'allure-results/**', allowEmptyArchive: true
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
    def sanitizedTag = serviceForUrl.toLowerCase()
            .replaceAll("@", "")
            .replaceAll("\\s+(and|or|not)\\s+", "-")
            .replaceAll("[^a-z0-9\\-_]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-\$", "")

    return "${baseUrl}/${sanitizedTag}/${serviceNameForUrl}/${buildPath}/index.html"
}

return this
