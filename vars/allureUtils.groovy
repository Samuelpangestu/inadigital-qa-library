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
 * Analyze test results and create meaningful categories
 */
/**
 * Fixed priority-based categories with correct grep patterns
 * Add this to your allureUtils.groovy
 */
def createPriorityBasedCategories() {
    echo "🔍 Analyzing test results for priority-based categorization..."

    def categories = []

    try {
        // Enhanced analysis with correct patterns
        def tagAnalysis = sh(
                script: '''
                cd allure-report/data/test-cases 2>/dev/null || cd target/allure-results 2>/dev/null || exit 0
                
                # Count tests by priority tags (fixed patterns)
                HIGH_COUNT=$(grep -l '"value":"high"' *.json 2>/dev/null | wc -l)
                MEDIUM_COUNT=$(grep -l '"value":"medium"' *.json 2>/dev/null | wc -l) 
                LOW_COUNT=$(grep -l '"value":"low"' *.json 2>/dev/null | wc -l)
                
                # Count by service tags
                INAGOV_COUNT=$(grep -l '"value":"inagov"' *.json 2>/dev/null | wc -l)
                INAPAS_COUNT=$(grep -l '"value":"inapas"' *.json 2>/dev/null | wc -l)
                PERURIID_COUNT=$(grep -l '"value":"peruriid"' *.json 2>/dev/null | wc -l)
                SBU_COUNT=$(grep -l '"value":"sbu"' *.json 2>/dev/null | wc -l)
                
                # Count by API type
                EXTERNAL_COUNT=$(grep -l '"value":"external-api"' *.json 2>/dev/null | wc -l)
                INTERNAL_COUNT=$(grep -l '"value":"internal-api"' *.json 2>/dev/null | wc -l)
                
                # Count by test type
                SMOKE_COUNT=$(grep -l '"value":"smoke"' *.json 2>/dev/null | wc -l)
                REGRESSION_COUNT=$(grep -l '"value":"regression' *.json 2>/dev/null | wc -l)
                
                # Count by status from test names
                TOTAL_COUNT=$(ls *.json 2>/dev/null | wc -l)
                FAILED_COUNT=$(grep -l '"status" : "failed"' *.json 2>/dev/null | wc -l)
                PASSED_COUNT=$(grep -l '"status" : "passed"' *.json 2>/dev/null | wc -l)
                BROKEN_COUNT=$(grep -l '"status" : "broken"' *.json 2>/dev/null | wc -l)
                
                echo "HIGH=$HIGH_COUNT,MEDIUM=$MEDIUM_COUNT,LOW=$LOW_COUNT,INAGOV=$INAGOV_COUNT,INAPAS=$INAPAS_COUNT,PERURIID=$PERURIID_COUNT,SBU=$SBU_COUNT,EXTERNAL=$EXTERNAL_COUNT,INTERNAL=$INTERNAL_COUNT,SMOKE=$SMOKE_COUNT,REGRESSION=$REGRESSION_COUNT,TOTAL=$TOTAL_COUNT,FAILED=$FAILED_COUNT,PASSED=$PASSED_COUNT,BROKEN=$BROKEN_COUNT"
            ''',
                returnStdout: true
        ).trim()

        echo "📊 Tag Analysis: ${tagAnalysis}"

        // Parse the counts
        def counts = [:]
        tagAnalysis.split(',').each { pair ->
            def (key, value) = pair.split('=')
            counts[key] = value as Integer
        }

        // Create categories based on actual data found

        // Priority Categories (if we have priority tags)
        if (counts.HIGH > 0) {
            categories.add([
                    name: "🔥 High Priority Tests (${counts.HIGH})",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"]
            ])
            categories.add([
                    name: "🚨 High Priority Failures",
                    matchedStatuses: ["failed", "broken"]
            ])
        }

        if (counts.MEDIUM > 0) {
            categories.add([
                    name: "⚡ Medium Priority Tests (${counts.MEDIUM})",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"]
            ])
        }

        if (counts.LOW > 0) {
            categories.add([
                    name: "📋 Low Priority Tests (${counts.LOW})",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"]
            ])
        }

        // Service Categories
        if (counts.INAGOV > 0) {
            categories.add([
                    name: "🏛️ INAGov Services (${counts.INAGOV})",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"]
            ])
        }

        if (counts.INAPAS > 0) {
            categories.add([
                    name: "🎫 INAPas Services (${counts.INAPAS})",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"]
            ])
        }

        if (counts.PERURIID > 0) {
            categories.add([
                    name: "🆔 PeruriID Services (${counts.PERURIID})",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"]
            ])
        }

        if (counts.SBU > 0) {
            categories.add([
                    name: "🏢 SBU Services (${counts.SBU})",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"]
            ])
        }

        // API Type Categories
        if (counts.EXTERNAL > 0) {
            categories.add([
                    name: "🌐 External API Tests (${counts.EXTERNAL})",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"],
                    traceRegex: ".*External API.*"
            ])
        }

        if (counts.INTERNAL > 0) {
            categories.add([
                    name: "🏠 Internal API Tests (${counts.INTERNAL})",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"],
                    traceRegex: ".*Internal API.*"
            ])
        }

        // Test Type Categories
        if (counts.SMOKE > 0) {
            categories.add([
                    name: "💨 Smoke Tests (${counts.SMOKE})",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"]
            ])
        }

        if (counts.REGRESSION > 0) {
            categories.add([
                    name: "🔄 Regression Tests (${counts.REGRESSION})",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"]
            ])
        }

    } catch (Exception e) {
        echo "⚠️ Error in analysis: ${e.getMessage()}, using fallback categories"
    }

    // Always add these status-based categories
    categories.addAll([
            [
                    name: "🚨 All Failed Tests",
                    matchedStatuses: ["failed", "broken"]
            ],
            [
                    name: "✅ All Passed Tests",
                    matchedStatuses: ["passed"]
            ],
            [
                    name: "⏭️ Skipped Tests",
                    matchedStatuses: ["skipped"]
            ],
            [
                    name: "🔍 External API by Name",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"],
                    messageRegex: ".*External API.*"
            ],
            [
                    name: "🏠 Internal API by Name",
                    matchedStatuses: ["passed", "failed", "broken", "skipped"],
                    messageRegex: ".*Internal API.*"
            ]
    ])

    // Write categories to the correct location
    def categoriesJson = groovy.json.JsonOutput.toJson(categories)

    // Try multiple locations
    def locations = [
            'allure-report/data/categories.json',
            'target/allure-results/categories.json'
    ]

    locations.each { location ->
        try {
            writeFile file: location, text: categoriesJson
            echo "✅ Created categories at: ${location}"
        } catch (Exception e) {
            echo "⚠️ Could not write to ${location}: ${e.getMessage()}"
        }
    }

    echo "📊 Generated ${categories.size()} categories based on analysis"
}

/**
 * Enhanced debug method to check what tags actually exist
 */
def debugTestStructure() {
    sh '''
        echo "=== DETAILED TAG ANALYSIS ==="
        
        if [ -d "allure-report/data/test-cases" ]; then
            cd allure-report/data/test-cases
            
            echo "📋 Checking actual tag patterns in files:"
            
            # Show a sample of what tags look like
            echo "🔍 Sample tag structure from first file:"
            head -50 *.json | grep -A2 -B2 '"name":"tag"' | head -20
            
            echo ""
            echo "📊 All unique tag values found:"
            grep -h '"name":"tag"' *.json | sed 's/.*"value":"//' | sed 's/".*//' | sort | uniq -c | sort -nr
            
            echo ""
            echo "🏷️ All unique label names:"
            grep -h '"name":' *.json | sed 's/.*"name":"//' | sed 's/".*//' | sort | uniq -c | sort -nr
            
        else
            echo "❌ No test cases found"
        fi
    '''
}

return this
