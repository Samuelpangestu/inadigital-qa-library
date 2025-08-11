#!/usr/bin/env groovy

/**
 * Enhanced Report Management Utilities - NaN Fix
 */

// =============================================================================
// ALLURE REPORT MANAGEMENT - ENHANCED WITH NaN FIXES
// =============================================================================

def prepareAllureHistory(String persistentHistoryDir) {
    def cleanDir = sanitizeHistoryPath(persistentHistoryDir)

    sh """
        echo "📊 Preparing history files for Allure report"
        mkdir -p allure-results/history

        if [ -d '${cleanDir}' ] && [ "\$(ls -la '${cleanDir}' 2>/dev/null | wc -l)" -gt "3" ]; then
            echo "📋 Copying history from: ${cleanDir}"
            cp -f '${cleanDir}'/* allure-results/history/ 2>/dev/null || true
            echo "✅ History files copied"
        else
            echo "ℹ️ No existing history found"
        fi
    """
}

def generateAllureReport(String persistentHistoryDir, String allureCommandPath) {
    def cleanDir = sanitizeHistoryPath(persistentHistoryDir)

    sh """
        export PATH="${allureCommandPath}:\$PATH"
        echo "🔄 Generating Allure report with enhanced error handling..."
        
        # 🔧 FIXED: Validate and clean JSON files before generation
        echo "🔍 Validating Allure result files..."
        
        if [ -d "allure-results" ]; then
            # Remove any malformed JSON files
            for json_file in allure-results/*.json; do
                if [ -f "\$json_file" ] && [ "\$(basename "\$json_file")" != "categories.json" ] && [ "\$(basename "\$json_file")" != "environment.properties" ]; then
                    echo "📋 Checking \$json_file"
                    
                    # Check if file is valid JSON
                    if ! jq empty "\$json_file" 2>/dev/null; then
                        echo "⚠️ Removing invalid JSON file: \$json_file"
                        rm -f "\$json_file"
                    else
                        # 🔧 FIXED: Remove problematic uuid fields from step results
                        echo "🔧 Cleaning UUID fields from \$json_file"
                        jq 'walk(if type == "object" and has("uuid") and has("steps") then del(.uuid) else . end)' "\$json_file" > "\$json_file.tmp" && mv "\$json_file.tmp" "\$json_file" || rm -f "\$json_file.tmp"
                    fi
                fi
            done
            
            # Ensure we have at least one result file
            if [ \$(find allure-results -name "*.json" -not -name "categories.json" -not -name "environment.properties" | wc -l) -eq 0 ]; then
                echo "⚠️ No valid result files found, creating minimal result"
                cat > allure-results/minimal-result.json << 'EOF'
{
  "name": "Test Execution",
  "status": "unknown",
  "stage": "finished",
  "start": 0,
  "stop": 0,
  "uuid": "\$(uuidgen || echo "default-uuid")"
}
EOF
            fi
        else
            echo "❌ No allure-results directory found"
            mkdir -p allure-results
            cat > allure-results/no-tests-result.json << 'EOF'
{
  "name": "No Tests Executed",
  "status": "skipped", 
  "stage": "finished",
  "start": 0,
  "stop": 0
}
EOF
        fi

        # Generate report with fallback
        if allure generate allure-results -o allure-report --clean; then
            echo "✅ Allure report generated successfully"
        else
            echo "⚠️ Allure generation failed, creating fallback report"
            mkdir -p allure-report
            cat > allure-report/index.html << 'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>Test Report - Generation Failed</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 40px; }
        .error { color: #d32f2f; background: #ffebee; padding: 20px; border-radius: 4px; }
        .info { color: #1976d2; background: #e3f2fd; padding: 20px; border-radius: 4px; margin-top: 20px; }
    </style>
</head>
<body>
    <h1>Test Report Generation Failed</h1>
    <div class="error">
        <h3>Error</h3>
        <p>The Allure report could not be generated due to compatibility issues.</p>
        <p>This is typically caused by version mismatches between Allure Playwright and Allure CLI.</p>
    </div>
    <div class="info">
        <h3>Solution</h3>
        <p>Check the Jenkins console logs for detailed error information.</p>
        <p>Consider updating Allure CLI to version 2.30.0 or newer.</p>
    </div>
</body>
</html>
EOF
        fi

        # Save history
        if [ -d "allure-report/history" ]; then
            echo "💾 Saving history to: ${cleanDir}"
            mkdir -p '${cleanDir}'
            cp -f allure-report/history/* '${cleanDir}'/ 2>/dev/null || true
            echo "✅ History saved"
        fi
    """
}

private def sanitizeHistoryPath(String originalPath) {
    def tag = originalPath.substring(originalPath.lastIndexOf('/') + 1)
    def cleanTag = tag.toLowerCase()
            .replaceAll('@', '')
            .replaceAll('\\s+', '-')
            .replaceAll('[^a-z0-9\\-_]', '-')
            .replaceAll('-+', '-')
            .replaceAll('^-|-$', '')

    return "/var/lib/jenkins/allure-history/${cleanTag}"
}

// =============================================================================
// ENHANCED STATISTICS COLLECTION - NaN PROTECTION
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

        // 🔧 FIXED: Enhanced null safety and type checking
        if (results.stats) {
            stats.passed = safeParseInt(results.stats.expected, 0)
            stats.failed = safeParseInt(results.stats.unexpected, 0)
            stats.skipped = safeParseInt(results.stats.skipped, 0)
            stats.flaky = safeParseInt(results.stats.flaky, 0)
            stats.total = stats.passed + stats.failed + stats.skipped

            echo "📈 Stats found - expected: ${results.stats.expected}, unexpected: ${results.stats.unexpected}, skipped: ${results.stats.skipped}, flaky: ${results.stats.flaky}"
        }

        // Fallback to suite counting if stats are empty
        if (stats.total == 0 && results.suites) {
            echo "📊 Stats empty, counting from suites..."
            results.suites.each { suite ->
                countSuiteStats(suite, stats)
            }
        }

        // 🔧 FIXED: Final validation - prevent NaN values
        stats = validateStats(stats)

        echo "📊 Web Test Statistics from JSON: Total=${stats.total}, Passed=${stats.passed}, Failed=${stats.failed}, Skipped=${stats.skipped}, Flaky=${stats.flaky}"
        return stats

    } catch (Exception e) {
        echo "❌ Error parsing JSON report: ${e.getMessage()}"
        return collectPlaywrightStatisticsFromHTML()
    }
}

def countSuiteStats(def suite, def stats) {
    if (suite.suites) {
        suite.suites.each { nestedSuite ->
            countSuiteStats(nestedSuite, stats)
        }
    }

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
        return createDefaultStats()
    }

    try {
        def htmlContent = readFile(htmlPath)

        def passedMatch = (htmlContent =~ /(\d+)\s+passed/)
        def failedMatch = (htmlContent =~ /(\d+)\s+failed/)
        def skippedMatch = (htmlContent =~ /(\d+)\s+skipped/)
        def flakyMatch = (htmlContent =~ /(\d+)\s+flaky/)

        def passed = safeParseInt(passedMatch ? passedMatch[0][1] : "0", 0)
        def failed = safeParseInt(failedMatch ? failedMatch[0][1] : "0", 0)
        def skipped = safeParseInt(skippedMatch ? skippedMatch[0][1] : "0", 0)
        def flaky = safeParseInt(flakyMatch ? flakyMatch[0][1] : "0", 0)
        def total = passed + failed + skipped

        def stats = [
                total: total,
                passed: passed,
                failed: failed,
                skipped: skipped,
                flaky: flaky
        ]

        // 🔧 FIXED: Validate stats before returning
        stats = validateStats(stats)

        echo "📊 Web Test Statistics from HTML: Total=${stats.total}, Passed=${stats.passed}, Failed=${stats.failed}, Skipped=${stats.skipped}, Flaky=${stats.flaky}"
        return stats

    } catch (Exception e) {
        echo "❌ Error parsing HTML report: ${e.getMessage()}"
        return createDefaultStats()
    }
}

// =============================================================================
// UTILITY FUNCTIONS - NaN PREVENTION
// =============================================================================

/**
 * Safely parse integer values, preventing NaN
 */
private def safeParseInt(def value, int defaultValue = 0) {
    if (value == null) return defaultValue

    try {
        if (value instanceof Integer) return value
        if (value instanceof String) {
            def trimmed = value.trim()
            if (trimmed.isEmpty()) return defaultValue
            return trimmed as Integer
        }
        return value as Integer
    } catch (Exception e) {
        echo "⚠️ Failed to parse '${value}' as integer, using default: ${defaultValue}"
        return defaultValue
    }
}

/**
 * Validate statistics object to prevent NaN values
 */
private def validateStats(def stats) {
    def validatedStats = [:]

    validatedStats.total = safeParseInt(stats.total, 0)
    validatedStats.passed = safeParseInt(stats.passed, 0)
    validatedStats.failed = safeParseInt(stats.failed, 0)
    validatedStats.skipped = safeParseInt(stats.skipped, 0)
    validatedStats.flaky = safeParseInt(stats.flaky, 0)

    // Ensure total is at least the sum of individual stats
    def calculatedTotal = validatedStats.passed + validatedStats.failed + validatedStats.skipped
    if (validatedStats.total < calculatedTotal) {
        validatedStats.total = calculatedTotal
    }

    echo "🔍 Validated stats - ensuring no NaN values"
    return validatedStats
}

/**
 * Create default stats when no data is available
 */
private def createDefaultStats() {
    return [
            total: 0,
            passed: 0,
            failed: 0,
            skipped: 0,
            flaky: 0
    ]
}

// =============================================================================
// ALLURE STATISTICS COLLECTION - ENHANCED
// =============================================================================

def collectAllureStatistics() {
    def stats = [total: 0, passed: 0, failed: 0, broken: 0, skipped: 0, groupedStats: [:]]

    if (fileExists('allure-report/data/suites.json')) {
        try {
            def suitesJson = readJSON file: 'allure-report/data/suites.json'
            echo "📊 Processing Allure suites data..."

            if (suitesJson.children) {
                suitesJson.children.each { suite ->
                    def suiteName = suite.name ?: "Unknown Suite"
                    stats.groupedStats[suiteName] = [:]

                    if (suite.children) {
                        suite.children.each { testCase ->
                            def testName = testCase.name ?: "Unknown Test"

                            if (!stats.groupedStats[suiteName].containsKey(testName)) {
                                stats.groupedStats[suiteName][testName] = [total: 0, passed: 0, failed: 0, broken: 0, skipped: 0]
                            }

                            stats.total++
                            stats.groupedStats[suiteName][testName].total++

                            def status = testCase.status ?: "unknown"
                            switch (status) {
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
                    }

                    // Calculate success rates with NaN protection
                    stats.groupedStats[suiteName].each { testName, testStats ->
                        testStats.successRate = testStats.total > 0 ?
                                Math.round((testStats.passed * 100.0 / testStats.total)) : 100
                    }
                }
            }

            echo "📊 API Test Statistics: Total: ${stats.total}, Passed: ${stats.passed}, Failed: ${stats.failed}, Broken: ${stats.broken}, Skipped: ${stats.skipped}"
        } catch (Exception e) {
            echo "❌ Error processing Allure suites: ${e.getMessage()}"
        }
    } else {
        echo "⚠️ Warning: suites.json file not found - API test statistics will not be available"
    }

    return stats
}

// =============================================================================
// STATISTICS STORAGE - ENHANCED
// =============================================================================

def storeApiStatistics(def stats, def env) {
    env.LOCAL_TEST_COUNT = safeParseInt(stats.total, 0).toString()
    env.PASSED_COUNT = safeParseInt(stats.passed, 0).toString()
    env.FAILED_COUNT = safeParseInt(stats.failed, 0).toString()
    env.BROKEN_COUNT = safeParseInt(stats.broken, 0).toString()
    env.SKIPPED_COUNT = safeParseInt(stats.skipped, 0).toString()

    try {
        env.GROUPED_SUITE_STATS = groovy.json.JsonOutput.toJson(stats.groupedStats ?: [:])
    } catch (Exception e) {
        echo "⚠️ Failed to serialize grouped stats: ${e.getMessage()}"
        env.GROUPED_SUITE_STATS = "{}"
    }

    echo "💾 API test statistics stored in environment variables"
}

def storeWebStatistics(def testStats, def env) {
    // 🔧 FIXED: Ensure all values are valid before storing
    def validatedStats = validateStats(testStats)

    env.TEST_TOTAL = validatedStats.total.toString()
    env.TEST_PASSED = validatedStats.passed.toString()
    env.TEST_FAILED = validatedStats.failed.toString()
    env.TEST_SKIPPED = validatedStats.skipped.toString()
    env.TEST_FLAKY = validatedStats.flaky.toString()

    echo "💾 Web test statistics stored in environment variables"
    echo "📊 TEST_TOTAL=${env.TEST_TOTAL}, TEST_PASSED=${env.TEST_PASSED}, TEST_FAILED=${env.TEST_FAILED}, TEST_SKIPPED=${env.TEST_SKIPPED}, TEST_FLAKY=${env.TEST_FLAKY}"
}

def storeMobileStatistics(def testStats, def env) {
    def validatedStats = validateStats(testStats)

    env.MOBILE_TEST_TOTAL = validatedStats.total.toString()
    env.MOBILE_TEST_PASSED = validatedStats.passed.toString()
    env.MOBILE_TEST_FAILED = validatedStats.failed.toString()
    env.MOBILE_TEST_SKIPPED = validatedStats.skipped.toString()

    echo "💾 Mobile test statistics stored in environment variables"
}

// =============================================================================
// ARTIFACT MANAGEMENT
// =============================================================================

def archiveApiArtifacts() {
    archiveArtifacts artifacts: 'allure-report/**', allowEmptyArchive: true
    archiveArtifacts artifacts: '.env*', allowEmptyArchive: true
    archiveArtifacts artifacts: 'target/allure-results/**', allowEmptyArchive: true
}

def archiveWebArtifacts() {
    archiveArtifacts artifacts: 'playwright-report/**', allowEmptyArchive: true
    archiveArtifacts artifacts: 'test-results/**', allowEmptyArchive: true
    archiveArtifacts artifacts: 'allure-report/**', allowEmptyArchive: true
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

// =============================================================================
// URL GENERATION
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
