#!/usr/bin/env groovy

/**
 * Allure Utilities - Enhanced with Tag-Based Categorization
 * Centralized Allure environment and category management with long-term strategy
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
Categorization=Tag-Based
AllureStrategy=Long-Term-Hybrid
"""

    writeFile file: 'target/allure-results/environment.properties', text: envContent
    echo "Set enhanced Allure environment properties for API tests: ${targetEnv}, ${tag}, ${serviceName}"
}

/**
 * Add enhanced categories for test results with tag-based categorization
 */
def addApiAllureCategoriesEnhanced() {
    def categoriesContent = '''[
  {
    "name": "Critical Failures",
    "matchedStatuses": ["failed", "broken"],
    "messageRegex": ".*(severity:critical|@critical|@high|@p1).*"
  },
  {
    "name": "Critical Smoke Tests",
    "matchedStatuses": ["passed", "failed", "broken", "skipped"],
    "messageRegex": ".*(severity:critical|@critical|@high).*(@smoke).*"
  },
  {
    "name": "High Priority Tests",
    "matchedStatuses": ["passed", "failed", "broken", "skipped"],
    "messageRegex": ".*(severity:critical|@critical|@high|@p1).*"
  },
  {
    "name": "Medium Priority Tests", 
    "matchedStatuses": ["passed", "failed", "broken", "skipped"],
    "messageRegex": ".*(severity:normal|@medium|@normal|@p2).*"
  },
  {
    "name": "Low Priority Tests",
    "matchedStatuses": ["passed", "failed", "broken", "skipped"], 
    "messageRegex": ".*(severity:minor|@low|@minor|@p3).*"
  },
  {
    "name": "Smoke Test Failures",
    "matchedStatuses": ["failed", "broken"],
    "messageRegex": ".*@smoke.*"
  },
  {
    "name": "Smoke Tests",
    "matchedStatuses": ["passed", "failed", "broken", "skipped"],
    "messageRegex": ".*(@smoke|testType:smoke).*"
  },
  {
    "name": "Regression Tests",
    "matchedStatuses": ["passed", "failed", "broken", "skipped"],
    "messageRegex": ".*(@regression|testType:regression).*"
  },
  {
    "name": "API Layer Tests",
    "matchedStatuses": ["passed", "failed", "broken", "skipped"],
    "messageRegex": ".*(@api|layer:api).*"
  },
  {
    "name": "UI Layer Tests",
    "matchedStatuses": ["passed", "failed", "broken", "skipped"],
    "messageRegex": ".*(@ui|layer:ui).*"
  },
  {
    "name": "INAGov Service Tests",
    "matchedStatuses": ["passed", "failed", "broken", "skipped"],
    "messageRegex": ".*(@inagov|service:inagov).*"
  },
  {
    "name": "INAPas Service Tests",
    "matchedStatuses": ["passed", "failed", "broken", "skipped"],
    "messageRegex": ".*(@inapas|service:inapas).*"
  },
  {
    "name": "INAKu Service Tests",
    "matchedStatuses": ["passed", "failed", "broken", "skipped"],
    "messageRegex": ".*(@inaku|service:inaku).*"
  },
  {
    "name": "SBU Service Tests",
    "matchedStatuses": ["passed", "failed", "broken", "skipped"],
    "messageRegex": ".*(@sbu|service:sbu).*"
  },
  {
    "name": "PeruriID Service Tests",
    "matchedStatuses": ["passed", "failed", "broken", "skipped"],
    "messageRegex": ".*(@peruriid|service:peruriid).*"
  },
  {
    "name": "MBG Service Tests",
    "matchedStatuses": ["passed", "failed", "broken", "skipped"],
    "messageRegex": ".*(@mbg|service:mbg).*"
  },
  {
    "name": "TelkomSign Service Tests",
    "matchedStatuses": ["passed", "failed", "broken", "skipped"],
    "messageRegex": ".*(@telkomsign|service:telkomsign).*"
  },
  {
    "name": "External API Issues",
    "matchedStatuses": ["passed", "failed", "broken", "skipped"],
    "traceRegex": ".*External API.*"
  },
  {
    "name": "Internal API Issues",
    "matchedStatuses": ["passed", "failed", "broken", "skipped"],
    "traceRegex": ".*Internal API.*"
  },
  {
    "name": "Connection Timeouts",
    "matchedStatuses": ["broken", "failed"],
    "traceRegex": ".*SocketTimeoutException.*Read timed out.*"
  },
  {
    "name": "Authentication Issues",
    "matchedStatuses": ["failed"],
    "messageRegex": ".*(401|403|unauthorized|forbidden).*"
  },
  {
    "name": "Data Validation Issues",
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
    "name": "Standard Failed Tests",
    "matchedStatuses": ["failed"]
  },
  {
    "name": "Broken Tests", 
    "matchedStatuses": ["broken"]
  },
  {
    "name": "Skipped Tests",
    "matchedStatuses": ["skipped"]
  }
]'''

    writeFile file: 'target/allure-results/categories.json', text: categoriesContent
    echo "Added enhanced Allure categories with comprehensive tag-based categorization"
}

/**
 * Complete API Allure setup with enhanced environment and categories
 */
def setupApiAllureReport(def params, def env) {
    // Ensure target directory exists
    sh 'mkdir -p target/allure-results'

    // Setup enhanced environment properties
    setupApiAllureEnvironment(params, env)

    // Add enhanced categories for better organization
    addApiAllureCategoriesEnhanced()

    // Generate allure.properties for better static serving
    generateAllureProperties()

    echo "Enhanced API Allure report setup completed with tag-based categorization"
}

/**
 * Generate enhanced allure.properties for better report configuration
 */
def generateAllureProperties() {
    def propertiesContent = '''allure.results.directory=target/allure-results
allure.link.issue.pattern=https://github.com/inadigital/issues/{}
allure.link.tms.pattern=https://inadigital.atlassian.net/browse/{}
allure.label.owner=QA Team
allure.label.layer=API
allure.label.host=Jenkins
'''

    writeFile file: 'allure.properties', text: propertiesContent
    echo "Generated enhanced allure.properties configuration"
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
Categorization=Tag-Based
AllureStrategy=Long-Term-Hybrid
"""

    writeFile file: 'allure-results/environment.properties', text: envContent
    echo "Set enhanced Allure environment properties for Web tests: ${targetEnv}, ${browser}, headless=${headless}"
}

/**
 * Add enhanced categories for Web test results
 */
def addWebAllureCategoriesEnhanced() {
    def categoriesContent = '''[
  {
    "name": "Critical Web Failures",
    "matchedStatuses": ["failed", "broken"],
    "messageRegex": ".*(severity:critical|@critical|@high).*"
  },
  {
    "name": "Smoke Test Failures",
    "matchedStatuses": ["failed", "broken"],
    "messageRegex": ".*@smoke.*"
  },
  {
    "name": "High Priority Web Tests",
    "matchedStatuses": ["passed", "failed", "broken", "skipped"],
    "messageRegex": ".*(severity:critical|@critical|@high).*"
  },
  {
    "name": "UI Test Failures",
    "matchedStatuses": ["failed"],
    "messageRegex": ".*(@ui|layer:ui).*"
  },
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
    "name": "UI Interaction Issues",
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
    echo "Added enhanced Allure categories for Web tests"
}

/**
 * Complete Web Allure setup with enhanced environment and categories
 */
def setupWebAllureReport(def params, def env) {
    // Setup enhanced environment properties
    setupWebAllureEnvironment(params, env)

    // Add enhanced categories for better organization
    addWebAllureCategoriesEnhanced()

    echo "Enhanced Web Allure report setup completed with tag-based categorization"
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
 * Add categories for test results - legacy method that delegates to enhanced version
 */
def addApiAllureCategories() {
    addApiAllureCategoriesEnhanced()
}

/**
 * Add categories for Web test results - legacy method that delegates to enhanced version
 */
def addWebAllureCategories() {
    addWebAllureCategoriesEnhanced()
}

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

return this
