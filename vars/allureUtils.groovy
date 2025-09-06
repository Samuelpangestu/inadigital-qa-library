#!/usr/bin/env groovy

/**
 * Allure Utilities - Complete API & Web Support
 * Centralized Allure environment and category management
 */

// =============================================================================
// BACKWARD COMPATIBILITY METHODS
// =============================================================================

def prepareHistory(String persistentHistoryDir) {
    // Delegate to reportManager for consistency
    reportManager.prepareAllureHistory(persistentHistoryDir)
}

def generateReport(String persistentHistoryDir, String allureCommandPath) {
    // Delegate to reportManager for consistency
    reportManager.generateAllureReport(persistentHistoryDir, allureCommandPath)
}

// =============================================================================
// API ALLURE ENVIRONMENT SETUP
// =============================================================================

/**
 * Setup Allure environment for API tests
 */
def setupApiAllureEnvironment(def params, def env) {
    def targetEnv = params.TARGET_ENV ?: 'dev'
    def tag = env.EFFECTIVE_QA_SERVICE ?: params.QA_SERVICE ?: 'api'
    def buildNumber = env.BUILD_NUMBER ?: 'unknown'
    def serviceName = params.QA_SERVICE_NAME ?: params.QA_SERVICE
    def sheets = env.EFFECTIVE_SHEET_NAMES ?: 'AUTO'
    def testResults = "${env.PASSED_COUNT ?: '0'} Passed, ${env.FAILED_COUNT ?: '0'} Failed, ${env.BROKEN_COUNT ?: '0'} Broken"

    def envContent = """Environment=${targetEnv}
Framework=RestAssured+Cucumber
Language=Java
Tag=${tag}
Service=${serviceName}
Build=${buildNumber}
Sheets=${sheets}
Jenkins_Job=${env.JOB_NAME ?: 'qa-api-automation'}
Maven_Version=${getMavenVersion()}
Java_Version=${getJavaVersion()}
"""

    writeFile file: 'target/allure-results/environment.properties', text: envContent
    echo "Set Allure environment properties for API tests: ${targetEnv}, ${tag}, ${serviceName}"
}

/**
 * Add categories for test results - restored to original working configuration
 */
/**
 * Add categories for test results with priority-based categorization
 */
/**
 * Add categories for test results with priority-based categorization
 * Enhanced version for better test organization
 */
def addApiAllureCategories() {
    def categoriesContent = '''[
  {
    "name": "🔥 Critical Priority - Failed",
    "matchedStatuses": ["failed", "broken"],
    "messageRegex": ".*🔥.*\\[HIGH\\].*"
  },
  {
    "name": "🔥 Critical Priority - Passed",
    "matchedStatuses": ["passed"],
    "messageRegex": ".*🔥.*\\[HIGH\\].*"
  },
  {
    "name": "⚡ Medium Priority - Failed", 
    "matchedStatuses": ["failed", "broken"],
    "messageRegex": ".*⚡.*\\[MEDIUM\\].*"
  },
  {
    "name": "⚡ Medium Priority - Passed",
    "matchedStatuses": ["passed"],
    "messageRegex": ".*⚡.*\\[MEDIUM\\].*"
  },
  {
    "name": "📋 Low Priority - Failed",
    "matchedStatuses": ["failed", "broken"],
    "messageRegex": ".*📋.*\\[LOW\\].*"
  },
  {
    "name": "📋 Low Priority - Passed",
    "matchedStatuses": ["passed"],
    "messageRegex": ".*📋.*\\[LOW\\].*"
  },
  {
    "name": "💨 Critical Smoke Tests",
    "matchedStatuses": ["passed", "failed", "broken"],
    "messageRegex": ".*🔥.*smoke.*"
  },
  {
    "name": "🔄 High Priority Regression",
    "matchedStatuses": ["passed", "failed", "broken"], 
    "messageRegex": ".*🔥.*regression.*"
  },
  {
    "name": "🏛️ INAGov - Critical Issues",
    "matchedStatuses": ["failed", "broken"],
    "messageRegex": ".*(🔥.*inagov|inagov.*🔥).*"
  },
  {
    "name": "🎫 INAPas - Critical Issues",
    "matchedStatuses": ["failed", "broken"],
    "messageRegex": ".*(🔥.*inapas|inapas.*🔥).*"
  },
  {
    "name": "🆔 PeruriID - Critical Issues",
    "matchedStatuses": ["failed", "broken"],
    "messageRegex": ".*(🔥.*peruriid|peruriid.*🔥).*"
  },
  {
    "name": "🏢 SBU - Critical Issues",
    "matchedStatuses": ["failed", "broken"],
    "messageRegex": ".*(🔥.*sbu|sbu.*🔥).*"
  },
  {
    "name": "External API",
    "matchedStatuses": ["passed", "failed", "broken", "skipped"],
    "traceRegex": ".*External API.*"
  },
  {
    "name": "Internal API",
    "matchedStatuses": ["passed", "failed", "broken", "skipped"],
    "traceRegex": ".*Internal API.*"
  },
  {
    "name": "Read timed out",
    "matchedStatuses": ["broken", "failed"],
    "traceRegex": ".*SocketTimeoutException.*Read timed out.*"
  },
  {
    "name": "Authentication Issues",
    "matchedStatuses": ["failed"],
    "messageRegex": ".*(401|403|unauthorized|forbidden).*"
  },
  {
    "name": "Data Issues",
    "matchedStatuses": ["failed"],
    "messageRegex": ".*(400|404|validation|schema).*"
  },
  {
    "name": "Server Issues",
    "matchedStatuses": ["failed"],
    "messageRegex": ".*(500|502|503|504).*"
  },
  {
    "name": "Infrastructure Issues",
    "matchedStatuses": ["broken", "failed"],
    "messageRegex": ".*(timeout|connection|network).*"
  },
  {
    "name": "Skipped Tests",
    "matchedStatuses": ["skipped"]
  }
]'''

    writeFile file: 'target/allure-results/categories.json', text: categoriesContent
    echo "Added enhanced Allure categories with priority-based categorization"
}

/**
 * Complete API Allure setup with environment and categories
 */
def setupApiAllureReport(def params, def env) {
    // Ensure target directory exists
    sh 'mkdir -p target/allure-results'

    // Setup environment properties
    setupApiAllureEnvironment(params, env)

    // Add categories for better organization
    addApiAllureCategories()

    // Generate allure.properties for better static serving
    generateAllureProperties()

    echo "API Allure report setup completed"
}

/**
 * Generate allure.properties for better report configuration
 */
def generateAllureProperties() {
    def propertiesContent = '''allure.results.directory=target/allure-results
allure.link.issue.pattern=https://example.org/issue/{}
allure.link.tms.pattern=https://example.org/tms/{}
'''

    writeFile file: 'allure.properties', text: propertiesContent
    echo "Generated allure.properties configuration"
}

// =============================================================================
// WEB ALLURE ENVIRONMENT SETUP
// =============================================================================

/**
 * Setup Allure environment for Web tests
 */
def setupWebAllureEnvironment(def params, def env) {
    def targetEnv = params.TARGET_ENV ?: 'dev'
    def browser = params.BROWSER ?: 'chromium'
    def headless = params.HEADLESS?.toString() ?: 'true'
    def tag = env.EFFECTIVE_QA_SERVICE ?: params.QA_SERVICE ?: 'test'
    def buildNumber = env.BUILD_NUMBER ?: 'unknown'
    def testResults = "${env.TEST_PASSED ?: '1'} Passed, ${env.TEST_FAILED ?: '0'} Failed"

    def envContent = """Environment=${targetEnv}
Browser=${browser}
Headless=${headless}
Tag=${tag}
Build=${buildNumber}
Framework=Playwright
Language=TypeScript
Jenkins_Job=${env.JOB_NAME ?: 'qa-web-automation'}
Node_Version=${getNodeVersion()}
Playwright_Version=${getPlaywrightVersion()}
"""

    writeFile file: 'allure-results/environment.properties', text: envContent
    echo "Set Allure environment properties for Web tests: ${targetEnv}, ${browser}, headless=${headless}"
}

/**
 * Add categories for Web test results
 */
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
  },
  {
    "name": "Navigation Issues",
    "matchedStatuses": ["failed"],
    "messageRegex": ".*(navigation|page load|redirect).*"
  },
  {
    "name": "Assertion Failures",
    "matchedStatuses": ["failed"],
    "messageRegex": ".*(expect|assertion|toBe|toHaveText).*"
  }
]'''

    writeFile file: 'allure-results/categories.json', text: categoriesContent
    echo "Added Allure categories for Web tests"
}

/**
 * Complete Web Allure setup with environment and categories
 */
def setupWebAllureReport(def params, def env) {
    // Setup environment properties
    setupWebAllureEnvironment(params, env)

    // Add categories for better organization
    addWebAllureCategories()

    echo "Web Allure report setup completed"
}

// =============================================================================
// VERSION DETECTION UTILITIES
// =============================================================================

def getMavenVersion() {
    try {
        return sh(script: 'mvn --version | head -1 | cut -d" " -f3', returnStdout: true).trim()
    } catch (Exception e) {
        return 'unknown'
    }
}

def getJavaVersion() {
    try {
        return sh(script: 'java -version 2>&1 | head -1 | cut -d\\" -f2', returnStdout: true).trim()
    } catch (Exception e) {
        return 'unknown'
    }
}

def getNodeVersion() {
    try {
        return sh(script: 'node --version', returnStdout: true).trim()
    } catch (Exception e) {
        return 'unknown'
    }
}

def getPlaywrightVersion() {
    try {
        return sh(script: 'npx playwright --version | cut -d" " -f2', returnStdout: true).trim()
    } catch (Exception e) {
        return 'unknown'
    }
}

// =============================================================================
// LEGACY ALLURE UTILITIES (For Backward Compatibility)
// =============================================================================

/**
 * Clean up old allure results before new test run
 */
def cleanAllureResults() {
    sh '''
        echo "Cleaning previous Allure results..."
        rm -rf target/allure-results/* || true
        rm -rf allure-report/* || true
        mkdir -p target/allure-results
    '''
}

/**
 * Copy additional files to allure results
 */
def copyAdditionalFiles(List<String> filePaths) {
    filePaths.each { filePath ->
        if (fileExists(filePath)) {
            sh "cp ${filePath} target/allure-results/ || true"
            echo "Copied ${filePath} to Allure results"
        }
    }
}

/**
 * Set Allure environment properties (legacy method)
 */
def setAllureEnvironment(Map environmentProps) {
    def envContent = environmentProps.collect { key, value ->
        "${key}=${value}"
    }.join('\n')

    writeFile file: 'target/allure-results/environment.properties', text: envContent
    echo "Set Allure environment properties"
}

/**
 * Add categories for test results (legacy method - delegates to API)
 */
def addAllureCategories() {
    addApiAllureCategories()
}

/**
 * Working solution for creating categories based on actual test data
 * Add this to your allureUtils.groovy
 */
def createWorkingCategories() {
    echo "🔍 Creating categories based on actual test data..."

    def categories = []
    def testCounts = [:]

    try {
        // First, let's examine one file to understand the actual structure
        def sampleAnalysis = sh(
                script: '''
                cd allure-report/data/test-cases 2>/dev/null || exit 0
                
                # Get the actual structure of one file
                if [ -f "*.json" ]; then
                    echo "=== SAMPLE FILE STRUCTURE ==="
                    ls *.json | head -1 | xargs cat | jq '.' 2>/dev/null || cat `ls *.json | head -1` | head -50
                fi
            ''',
                returnStdout: true
        ).trim()

        echo "📋 Sample file structure: ${sampleAnalysis}"

        // Extract actual tag data using a more robust approach
        def tagData = sh(
                script: '''
                cd allure-report/data/test-cases 2>/dev/null || exit 0
                
                # Count actual patterns that exist in your files
                TOTAL_FILES=$(ls *.json 2>/dev/null | wc -l)
                
                # Count by test names (which we can see from your JSON example)
                EXTERNAL_API_COUNT=$(grep -l "External API" *.json 2>/dev/null | wc -l)
                INTERNAL_API_COUNT=$(grep -l "Internal API" *.json 2>/dev/null | wc -l)
                
                # Count by status
                FAILED_COUNT=$(grep -l '"status" : "failed"' *.json 2>/dev/null | wc -l)
                PASSED_COUNT=$(grep -l '"status" : "passed"' *.json 2>/dev/null | wc -l)
                BROKEN_COUNT=$(grep -l '"status" : "broken"' *.json 2>/dev/null | wc -l)
                
                # Look for actual tag patterns in labels array
                INAGOV_COUNT=$(grep -l '"value":"inagov"' *.json 2>/dev/null | wc -l)
                BKN_COUNT=$(grep -l '"value":"bkn"' *.json 2>/dev/null | wc -l)
                PERSONAL_DATA_COUNT=$(grep -l '"value":"personal-data"' *.json 2>/dev/null | wc -l)
                STAGING_COUNT=$(grep -l '"value":"staging"' *.json 2>/dev/null | wc -l)
                REGRESSION_COUNT=$(grep -l '"value":"regression' *.json 2>/dev/null | wc -l)
                
                echo "TOTAL=$TOTAL_FILES,EXTERNAL_API=$EXTERNAL_API_COUNT,INTERNAL_API=$INTERNAL_API_COUNT,FAILED=$FAILED_COUNT,PASSED=$PASSED_COUNT,BROKEN=$BROKEN_COUNT,INAGOV=$INAGOV_COUNT,BKN=$BKN_COUNT,PERSONAL_DATA=$PERSONAL_DATA_COUNT,STAGING=$STAGING_COUNT,REGRESSION=$REGRESSION_COUNT"
            ''',
                returnStdout: true
        ).trim()

        echo "📊 Actual tag analysis: ${tagData}"

        // Parse the actual counts
        def counts = [:]
        tagData.split(',').each { pair ->
            def parts = pair.split('=')
            if (parts.length == 2) {
                counts[parts[0]] = parts[1] as Integer
            }
        }

        // Create categories based on what actually exists in your tests

        // API Type Categories (based on test names)
        if (counts.EXTERNAL_API > 0) {
            categories.add([
                    name: "🌐 External API Tests (${counts.EXTERNAL_API})",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"],
                    messageRegex: ".*External API.*"
            ])
        }

        if (counts.INTERNAL_API > 0) {
            categories.add([
                    name: "🏠 Internal API Tests (${counts.INTERNAL_API})",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"],
                    messageRegex: ".*Internal API.*"
            ])
        }

        // Service Categories (based on actual tags found)
        if (counts.INAGOV > 0) {
            categories.add([
                    name: "🏛️ INAGov Services (${counts.INAGOV})",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"]
            ])
        }

        if (counts.BKN > 0) {
            categories.add([
                    name: "🔗 BKN Integration (${counts.BKN})",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"]
            ])
        }

        if (counts.PERSONAL_DATA > 0) {
            categories.add([
                    name: "📊 Personal Data APIs (${counts.PERSONAL_DATA})",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"]
            ])
        }

        if (counts.REGRESSION > 0) {
            categories.add([
                    name: "🔄 Regression Tests (${counts.REGRESSION})",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"]
            ])
        }

        if (counts.STAGING > 0) {
            categories.add([
                    name: "🎭 Staging Environment (${counts.STAGING})",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"]
            ])
        }

    } catch (Exception e) {
        echo "⚠️ Error in enhanced analysis: ${e.getMessage()}"
    }

    // Always include these status-based categories
    categories.addAll([
            [
                    name: "🚨 Failed Tests",
                    matchedStatuses: ["failed", "broken"]
            ],
            [
                    name: "✅ Passed Tests",
                    matchedStatuses: ["passed"]
            ],
            [
                    name: "⏭️ Skipped Tests",
                    matchedStatuses: ["skipped"]
            ],
            [
                    name: "🌐 External APIs",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"],
                    messageRegex: ".*External API.*"
            ],
            [
                    name: "🏠 Internal APIs",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"],
                    messageRegex: ".*Internal API.*"
            ],
            [
                    name: "📊 Data Services",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"],
                    messageRegex: ".*(Data|data).*"
            ]
    ])

    // Write categories to both locations
    def categoriesJson = groovy.json.JsonOutput.toJson(categories)

    // Write to target (for next run)
    writeFile file: 'target/allure-results/categories.json', text: categoriesJson

    // Write to allure-report (for current report)
    writeFile file: 'allure-report/data/categories.json', text: categoriesJson

    echo "✅ Created ${categories.size()} working categories"

    // Also let's examine what the current categories file contains
    if (fileExists('allure-report/widgets/categories.json')) {
        def widgetContent = readFile('allure-report/widgets/categories.json')
        echo "📋 Current widget categories: ${widgetContent}"
    }

    return categories
}

/**
 * Debug method to see what's actually in a test file
 */
def examineTestFile() {
    sh '''
        echo "=== EXAMINING ACTUAL TEST FILE CONTENT ==="
        
        cd allure-report/data/test-cases 2>/dev/null || exit 0
        
        # Get the first file and show its content
        FIRST_FILE=$(ls *.json | head -1)
        echo "📁 Examining file: $FIRST_FILE"
        echo ""
        
        echo "🏷️ Looking for labels section:"
        grep -A 20 '"labels"' "$FIRST_FILE" || echo "No labels section found"
        echo ""
        
        echo "📝 Test name:"
        grep '"name"' "$FIRST_FILE" | head -3
        echo ""
        
        echo "📊 Status:"
        grep '"status"' "$FIRST_FILE"
        echo ""
        
        echo "🔍 Full structure (first 100 lines):"
        head -100 "$FIRST_FILE"
    '''
}

return this
