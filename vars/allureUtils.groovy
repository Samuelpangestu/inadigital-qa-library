#!/usr/bin/env groovy

/**
 * Allure Utilities - Complete API & Web Support
 * Centralized Allure environment and category management
 */

// =============================================================================
// BACKWARD COMPATIBILITY METHODS
// =============================================================================

def prepareHistory(String persistentHistoryDir) {
    reportManager.prepareAllureHistory(persistentHistoryDir)
}

def generateReport(String persistentHistoryDir, String allureCommandPath) {
    reportManager.generateAllureReport(persistentHistoryDir, allureCommandPath)
}

// =============================================================================
// API ALLURE ENVIRONMENT SETUP
// =============================================================================

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

def addApiAllureCategories() {
    def categoriesContent = '''[
  {
    "name": "External API",
    "matchedStatuses": ["passed", "failed", "broken", "skipped"],
    "traceRegex": ".*external.*"
  },
  {
    "name": "Internal API", 
    "matchedStatuses": ["passed", "failed", "broken", "skipped"],
    "traceRegex": ".*internal.*"
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
    "name": "Failed API Tests",
    "matchedStatuses": ["failed"]
  },
  {
    "name": "Broken API Tests", 
    "matchedStatuses": ["broken"]
  },
  {
    "name": "Skipped Tests",
    "matchedStatuses": ["skipped"]
  }
]'''

    writeFile file: 'target/allure-results/categories.json', text: categoriesContent
    echo "Added Allure categories with External/Internal API support"
}

def setupApiAllureReport(def params, def env) {
    setupApiAllureEnvironment(params, env)
    addApiAllureCategories()
    echo "API Allure report setup completed"
}

// =============================================================================
// WEB ALLURE ENVIRONMENT SETUP
// =============================================================================

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

def setupWebAllureReport(def params, def env) {
    setupWebAllureEnvironment(params, env)
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

def cleanAllureResults() {
    sh '''
        echo "Cleaning previous Allure results..."
        rm -rf target/allure-results/* || true
        rm -rf allure-report/* || true
        mkdir -p target/allure-results
    '''
}

def copyAdditionalFiles(List<String> filePaths) {
    filePaths.each { filePath ->
        if (fileExists(filePath)) {
            sh "cp ${filePath} target/allure-results/ || true"
            echo "Copied ${filePath} to Allure results"
        }
    }
}

def setAllureEnvironment(Map environmentProps) {
    def envContent = environmentProps.collect { key, value ->
        "${key}=${value}"
    }.join('\n')

    writeFile file: 'target/allure-results/environment.properties', text: envContent
    echo "Set Allure environment properties"
}

def addAllureCategories() {
    addApiAllureCategories()
}

return this
