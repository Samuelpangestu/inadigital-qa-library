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
def addApiAllureCategories() {
    def categoriesContent = '''[
  {
    "name": "External API Tests",
    "matchedStatuses": ["passed", "failed", "broken", "skipped"],
    "traceRegex": ".*External API.*"
  },
  {
    "name": "Internal API Tests",
    "matchedStatuses": ["passed", "failed", "broken", "skipped"], 
    "traceRegex": ".*Internal API.*"
  },
  {
    "name": "Failed Tests",
    "matchedStatuses": ["failed"]
  },
  {
    "name": "Passed Tests",
    "matchedStatuses": ["passed"]
  }
]'''

    writeFile file: 'target/allure-results/categories.json', text: categoriesContent
    echo "Added traceRegex categories"
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

return this
