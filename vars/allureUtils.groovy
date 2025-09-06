#!/usr/bin/env groovy

/**
 * Allure Utilities - Complete API & Web Support
 * Centralized Allure environment and category management with tag-based categories
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
 * Enhanced categories for test results with tag-based categorization
 */
def addApiAllureCategories(def params, def env) {
    def currentTag = env.EFFECTIVE_QA_SERVICE ?: params.QA_SERVICE ?: 'api'

    // Build tag-based categories dynamically
    def tagCategories = buildTagBasedCategories(currentTag)

    // Combine with error-based categories
    def errorCategories = getErrorBasedCategories()

    def allCategories = tagCategories + errorCategories

    def categoriesContent = groovy.json.JsonOutput.toJson(allCategories)

    writeFile file: 'target/allure-results/categories.json', text: categoriesContent
    echo "Added Allure categories with tag-based and error-based categorization for: ${currentTag}"
}

/**
 * Build categories based on current test tags for Cucumber/Allure
 */
def buildTagBasedCategories(String currentTag) {
    def categories = []

    // API Type Categories (most important for your use case)
    categories.add([
            name: "External API Tests",
            matchedStatuses: ["passed", "failed", "broken", "skipped"],
            messageRegex: ".*external-api.*"
    ])

    categories.add([
            name: "Internal API Tests",
            matchedStatuses: ["passed", "failed", "broken", "skipped"],
            messageRegex: ".*internal-api.*"
    ])

    // Priority/Severity Categories
    categories.add([
            name: "High Priority Tests",
            matchedStatuses: ["passed", "failed", "broken", "skipped"],
            messageRegex: ".*\\bhigh\\b.*"
    ])

    categories.add([
            name: "Medium Priority Tests",
            matchedStatuses: ["passed", "failed", "broken", "skipped"],
            messageRegex: ".*\\bmedium\\b.*"
    ])

    categories.add([
            name: "Low Priority Tests",
            matchedStatuses: ["passed", "failed", "broken", "skipped"],
            messageRegex: ".*\\blow\\b.*"
    ])

    // Test Type Categories
    categories.add([
            name: "Login Tests",
            matchedStatuses: ["passed", "failed", "broken", "skipped"],
            messageRegex: ".*\\blogin\\b.*"
    ])

    categories.add([
            name: "Smoke Tests",
            matchedStatuses: ["passed", "failed", "broken", "skipped"],
            messageRegex: ".*\\bsmoke\\b.*"
    ])

    categories.add([
            name: "Regression Tests",
            matchedStatuses: ["passed", "failed", "broken", "skipped"],
            messageRegex: ".*regression.*"
    ])

    categories.add([
            name: "Positive Tests",
            matchedStatuses: ["passed", "failed", "broken", "skipped"],
            messageRegex: ".*\\bpositive\\b.*"
    ])

    categories.add([
            name: "Negative Tests",
            matchedStatuses: ["passed", "failed", "broken", "skipped"],
            messageRegex: ".*\\bnegative\\b.*"
    ])

    // Domain/Feature Categories
    categories.add([
            name: "Personal Data Tests",
            matchedStatuses: ["passed", "failed", "broken", "skipped"],
            messageRegex: ".*personal-data.*"
    ])

    categories.add([
            name: "BKN Integration Tests",
            matchedStatuses: ["passed", "failed", "broken", "skipped"],
            messageRegex: ".*\\bbkn\\b.*"
    ])

    categories.add([
            name: "Pemsos Tests",
            matchedStatuses: ["passed", "failed", "broken", "skipped"],
            messageRegex: ".*\\bpemsos\\b.*"
    ])

    // Service-specific Categories (based on current tag)
    if (currentTag.contains('inagov')) {
        categories.add([
                name: "INAGov Service Tests",
                matchedStatuses: ["passed", "failed", "broken", "skipped"],
                messageRegex: ".*\\binagov\\b.*"
        ])
    }

    if (currentTag.contains('inapas')) {
        categories.add([
                name: "INAPas Service Tests",
                matchedStatuses: ["passed", "failed", "broken", "skipped"],
                messageRegex: ".*\\binapas\\b.*"
        ])
    }

    if (currentTag.contains('sbu')) {
        categories.add([
                name: "SBU Service Tests",
                matchedStatuses: ["passed", "failed", "broken", "skipped"],
                messageRegex: ".*\\bsbu\\b.*"
        ])
    }

    if (currentTag.contains('peruriid')) {
        categories.add([
                name: "PeruriID Service Tests",
                matchedStatuses: ["passed", "failed", "broken", "skipped"],
                messageRegex: ".*\\bperuriid\\b.*"
        ])
    }

    return categories
}

/**
 * Get error-based categories (existing functionality)
 */
def getErrorBasedCategories() {
    return [
            [
                    name: "Read Timed Out",
                    matchedStatuses: ["broken", "failed"],
                    traceRegex: ".*SocketTimeoutException.*Read timed out.*"
            ],
            [
                    name: "Authentication Issues",
                    matchedStatuses: ["failed"],
                    messageRegex: ".*(401|403|unauthorized|forbidden).*"
            ],
            [
                    name: "Data Issues",
                    matchedStatuses: ["failed"],
                    messageRegex: ".*(400|404|validation|schema).*"
            ],
            [
                    name: "Server Issues",
                    matchedStatuses: ["failed"],
                    messageRegex: ".*(500|502|503|504).*"
            ],
            [
                    name: "Infrastructure Issues",
                    matchedStatuses: ["broken", "failed"],
                    messageRegex: ".*(timeout|connection|network).*"
            ],
            [
                    name: "Failed Tests",
                    matchedStatuses: ["failed"]
            ],
            [
                    name: "Broken Tests",
                    matchedStatuses: ["broken"]
            ],
            [
                    name: "Skipped Tests",
                    matchedStatuses: ["skipped"]
            ]
    ]
}

/**
 * Complete API Allure setup with environment and enhanced categories
 */
def setupApiAllureReport(def params, def env) {
    // Ensure target directory exists
    sh 'mkdir -p target/allure-results'

    // Setup environment properties
    setupApiAllureEnvironment(params, env)

    // Add enhanced categories for better organization
    addApiAllureCategories(params, env)

    // Generate allure.properties for better static serving
    generateAllureProperties()

    echo "API Allure report setup completed with tag-based categories"
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
 * Add categories for Web test results with tag support
 */
def addWebAllureCategories(def params, def env) {
    def currentTag = env.EFFECTIVE_QA_SERVICE ?: params.QA_SERVICE ?: 'test'

    // Build tag-based categories for web tests
    def tagCategories = buildWebTagCategories(currentTag)

    // Web-specific error categories
    def webErrorCategories = [
            [
                    name: "UI Issues",
                    matchedStatuses: ["failed"],
                    messageRegex: ".*(timeout|element not found|selector).*"
            ],
            [
                    name: "Navigation Issues",
                    matchedStatuses: ["failed"],
                    messageRegex: ".*(navigation|page load|redirect).*"
            ],
            [
                    name: "Assertion Failures",
                    matchedStatuses: ["failed"],
                    messageRegex: ".*(expect|assertion|toBe|toHaveText).*"
            ],
            [
                    name: "Flaky Tests",
                    matchedStatuses: ["failed"],
                    messageRegex: ".*retry.*"
            ],
            [
                    name: "Failed Tests",
                    matchedStatuses: ["failed"]
            ],
            [
                    name: "Broken Tests",
                    matchedStatuses: ["broken"]
            ],
            [
                    name: "Skipped Tests",
                    matchedStatuses: ["skipped"]
            ]
    ]

    def allCategories = tagCategories + webErrorCategories
    def categoriesContent = groovy.json.JsonOutput.toJson(allCategories)

    writeFile file: 'allure-results/categories.json', text: categoriesContent
    echo "Added Allure categories for Web tests with tag support"
}

/**
 * Build web-specific tag categories
 */
def buildWebTagCategories(String currentTag) {
    def categories = []

    // Common web test categories
    categories.add([
            name: "Login Tests",
            matchedStatuses: ["passed", "failed", "broken", "skipped"],
            traceRegex: ".*@login.*"
    ])

    categories.add([
            name: "Smoke Tests",
            matchedStatuses: ["passed", "failed", "broken", "skipped"],
            traceRegex: ".*@smoke.*"
    ])

    categories.add([
            name: "Regression Tests",
            matchedStatuses: ["passed", "failed", "broken", "skipped"],
            traceRegex: ".*@regression.*"
    ])

    // Priority categories
    categories.add([
            name: "High Priority",
            matchedStatuses: ["passed", "failed", "broken", "skipped"],
            traceRegex: ".*@high.*"
    ])

    return categories
}

/**
 * Complete Web Allure setup with environment and categories
 */
def setupWebAllureReport(def params, def env) {
    // Setup environment properties
    setupWebAllureEnvironment(params, env)

    // Add categories for better organization
    addWebAllureCategories(params, env)

    echo "Web Allure report setup completed with tag-based categories"
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
 * Add categories for test results (legacy method - delegates to enhanced API)
 */
def addAllureCategories() {
    // Use default parameters for backward compatibility
    def mockParams = [QA_SERVICE: 'api']
    def mockEnv = [EFFECTIVE_QA_SERVICE: 'api']
    addApiAllureCategories(mockParams, mockEnv)
}

return this
