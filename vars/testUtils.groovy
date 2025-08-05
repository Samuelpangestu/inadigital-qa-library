def determineTagToUse(String jobName, String defaultTag) {
    def jobNameLower = jobName.toLowerCase()

    if (jobNameLower.contains('inagov')) return 'inagov'
    if (jobNameLower.contains('inapas')) return 'inapas'
    if (jobNameLower.contains('inaku')) return 'inaku'
    if (jobNameLower.contains('sbu')) return 'sbu'
    if (jobNameLower.contains('emeterai-smoke')) return 'emeterai and @high'
    if (jobNameLower.contains('mbg')) return 'mbg'
    if (jobNameLower.contains('peruriid')) return 'peruriid'

    return defaultTag
}

// =============================================================================
// API TEST STATISTICS (Allure-based)
// =============================================================================

def collectStatistics() {
    def stats = [total: 0, passed: 0, failed: 0, broken: 0, skipped: 0, groupedStats: [:]]

    if (fileExists('allure-report/data/suites.json')) {
        def suitesJson = readJSON file: 'allure-report/data/suites.json'

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

        echo "API Test Statistics: Total: ${stats.total}, Passed: ${stats.passed}, Failed: ${stats.failed}, Broken: ${stats.broken}, Skipped: ${stats.skipped}"
    } else {
        echo "Warning: suites.json file not found - API test statistics will not be available"
    }

    return stats
}

def storeStatistics(def stats, def env) {
    env.LOCAL_TEST_COUNT = stats.total.toString()
    env.PASSED_COUNT = stats.passed.toString()
    env.FAILED_COUNT = stats.failed.toString()
    env.BROKEN_COUNT = stats.broken.toString()
    env.SKIPPED_COUNT = stats.skipped.toString()
    env.GROUPED_SUITE_STATS = groovy.json.JsonOutput.toJson(stats.groupedStats)

    echo "API test statistics stored in environment variables"
}

// =============================================================================
// WEB TEST STATISTICS (Playwright JSON-based)
// =============================================================================

def collectWebTestStatisticsFromJSON() {
    def jsonPath = "test-results/result.json"

    if (!fileExists(jsonPath)) {
        echo "JSON report not found at: ${jsonPath}, falling back to HTML parsing"
        return collectWebTestStatisticsFromHTML()
    }

    try {
        def jsonContent = readFile(jsonPath)
        def results = readJSON text: jsonContent

        echo "Processing Playwright JSON report..."

        // Simple direct parsing - use stats object
        def total = 0
        def passed = 0
        def failed = 0
        def skipped = 0
        def flaky = 0

        // Try stats first (Playwright v1.53+ format)
        if (results.stats) {
            passed = (results.stats.expected ?: 0) as Integer
            failed = (results.stats.unexpected ?: 0) as Integer
            skipped = (results.stats.skipped ?: 0) as Integer
            flaky = (results.stats.flaky ?: 0) as Integer
            total = passed + failed + skipped

            echo "Stats found - expected: ${results.stats.expected}, unexpected: ${results.stats.unexpected}, skipped: ${results.stats.skipped}, flaky: ${results.stats.flaky}"
        }

        // If stats didn't give us results, try counting suites
        if (total == 0 && results.suites) {
            echo "Stats empty, counting from suites..."
            results.suites.each { suite ->
                // Count nested suites
                if (suite.suites) {
                    suite.suites.each { nestedSuite ->
                        if (nestedSuite.specs) {
                            nestedSuite.specs.each { spec ->
                                total++
                                if (spec.tests && spec.tests.size() > 0) {
                                    def testResult = spec.tests[0]
                                    switch (testResult.status) {
                                        case 'expected':
                                            passed++
                                            break
                                        case 'unexpected':
                                            failed++
                                            break
                                        case 'flaky':
                                            flaky++
                                            passed++
                                            break
                                        case 'skipped':
                                            skipped++
                                            break
                                    }
                                }
                            }
                        }
                    }
                }

                // Count direct specs
                if (suite.specs) {
                    suite.specs.each { spec ->
                        total++
                        if (spec.tests && spec.tests.size() > 0) {
                            def testResult = spec.tests[0]
                            switch (testResult.status) {
                                case 'expected':
                                    passed++
                                    break
                                case 'unexpected':
                                    failed++
                                    break
                                case 'flaky':
                                    flaky++
                                    passed++
                                    break
                                case 'skipped':
                                    skipped++
                                    break
                            }
                        }
                    }
                }
            }
        }

        def stats = [
                total: total,
                passed: passed,
                failed: failed,
                skipped: skipped,
                flaky: flaky
        ]

        echo "Web Test Statistics from JSON: Total=${total}, Passed=${passed}, Failed=${failed}, Skipped=${skipped}, Flaky=${flaky}"

        return stats

    } catch (Exception e) {
        echo "Error parsing JSON report: ${e.getMessage()}"
        return collectWebTestStatisticsFromHTML()
    }
}

def collectWebTestStatisticsFromHTML() {
    def htmlPath = "playwright-report/index.html"

    if (!fileExists(htmlPath)) {
        echo "HTML report not found at: ${htmlPath}"
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

        echo "Web Test Statistics from HTML: Total=${total}, Passed=${passed}, Failed=${failed}, Skipped=${skipped}, Flaky=${flaky}"

        return [
                total: total,
                passed: passed,
                failed: failed,
                skipped: skipped,
                flaky: flaky
        ]

    } catch (Exception e) {
        echo "Error parsing HTML report: ${e.getMessage()}"
        return [total: 0, passed: 0, failed: 0, skipped: 0, flaky: 0]
    }
}

def storeWebTestStatistics(def testStats, def env) {
    env.TEST_TOTAL = testStats.total.toString()
    env.TEST_PASSED = testStats.passed.toString()
    env.TEST_FAILED = testStats.failed.toString()
    env.TEST_SKIPPED = testStats.skipped.toString()
    env.TEST_FLAKY = testStats.flaky ? testStats.flaky.toString() : "0"

    echo "Web test statistics stored in environment variables"
    echo "TEST_TOTAL=${env.TEST_TOTAL}, TEST_PASSED=${env.TEST_PASSED}, TEST_FAILED=${env.TEST_FAILED}, TEST_SKIPPED=${env.TEST_SKIPPED}, TEST_FLAKY=${env.TEST_FLAKY}"
}

// =============================================================================
// GOOGLE SHEETS TEST DATA LOADING
// =============================================================================

/**
 * Maps a tag/service to corresponding Google Sheet names
 * @param tagToUse The service tag to map
 * @return List of sheet names to load data from
 */
def mapTagToSheets(String tagToUse) {
    def sheetMapping = [
            'peruriid': ['PERURIID'],
            'external-iam': ['PERURIID'],
            'internal-iam': ['PERURIID'],
            'sbu': ['SBU'],
            'digidoc': ['SBU'],
            'digitrust': ['SBU'],
            'cmp': ['SBU'],
            'emeterai': ['SBU'],  // Changed from 'emeterai-smoke' to 'emeterai'
            'meterai': ['SBU'],   // Added meterai mapping
            'metel': ['SBU'],     // Added metel mapping
            'regression': ['PERURIID', 'SBU', 'INAGOV', 'INAPAS', 'INAKU'], // Changed from 'regression-all-services'
            'inagov': ['INAGOV'],
            'inapas': ['INAPAS'],
            'inaku': ['INAKU'],
            'wizard': ['PERURIID'],
            'mbg': ['MBG'],
            'positive': ['PERURIID', 'SBU', 'INAGOV', 'INAPAS', 'INAKU'], // Common tags
            'negative': ['PERURIID', 'SBU', 'INAGOV', 'INAPAS', 'INAKU'],
            'login': ['PERURIID', 'SBU', 'INAGOV', 'INAPAS', 'INAKU'],
            'smoke': ['PERURIID', 'SBU', 'INAGOV', 'INAPAS', 'INAKU'],
            'api': ['PERURIID', 'SBU', 'INAGOV', 'INAPAS', 'INAKU']
    ]

    def normalizedTag = tagToUse.toLowerCase().trim()

    // Find matching keys using contains logic
    for (def entry : sheetMapping) {
        if (normalizedTag.contains(entry.key)) {
            return entry.value
        }
    }

    // Fallback: return all sheets when no tag is found in the mapping
    return ['PERURIID', 'SBU', 'INAGOV', 'INAPAS', 'INAKU']
}

/**
 * Get effective sheet names based on tag and optional override
 * @param tagToUse The service tag determined from job
 * @param overrideSheetName Optional sheet name override from parameters
 * @return List of sheet names to use
 */
def getEffectiveSheetNames(String tagToUse, String overrideSheetName = null) {
    if (overrideSheetName?.trim()) {
        // If override is provided, split by comma and trim each name
        return overrideSheetName.split(',').collect { it.trim() }.findAll { it }
    } else {
        // Use the mapping strategy
        return mapTagToSheets(tagToUse)
    }
}

/**
 * Load test data from Google Sheets for specified sheet names
 * @param sheetNames List of sheet names to load data from
 * @param spreadsheetId Google Sheets spreadsheet ID (optional, uses default from TestData class)
 */
def loadTestDataFromSheets(List<String> sheetNames, String spreadsheetId = null) {
    echo "Loading test data from ${sheetNames.size()} sheet(s): ${sheetNames.join(', ')}"

    for (String sheetName : sheetNames) {
        echo "Processing sheet: ${sheetName}"

        try {
            // Clear any existing SHEET environment variable and set the new one
            sh """
                sed -i '/^SHEET=/d' .env || true
                echo "SHEET=${sheetName}" >> .env
                
                # Set spreadsheet ID if provided
                ${spreadsheetId ? "sed -i '/^SPREADSHEET_ID=/d' .env || true" : ""}
                ${spreadsheetId ? "echo 'SPREADSHEET_ID=${spreadsheetId}' >> .env" : ""}
                
                # Load test data using Maven
                export MAVEN_OPTS="-Xmx1024m -Dfile.encoding=UTF-8"
                mvn exec:java -Dexec.mainClass="inadigital.test_data.TestData" -q
            """

            echo "✓ Successfully loaded data from sheet: ${sheetName}"

        } catch (Exception e) {
            echo "⚠️ Warning: Failed to load data from sheet '${sheetName}': ${e.getMessage()}"
            // Continue with other sheets instead of failing completely
        }
    }

    echo "All test data loading completed"
}

/**
 * Simplified method for backward compatibility
 * Loads test data based on the determined tag
 */
def loadTestDataForService(String serviceTag) {
    def sheetNames = mapTagToSheets(serviceTag)
    loadTestDataFromSheets(sheetNames)
}

// =============================================================================
// BACKWARD COMPATIBILITY METHODS
// =============================================================================

def collectWebTestStatistics() {
    return collectWebTestStatisticsFromJSON()
}

return this
