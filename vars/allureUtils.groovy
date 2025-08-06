#!/usr/bin/env groovy

/**
 * Allure Utilities - Simplified and Delegated
 * This file now serves as a bridge to the reportManager for backward compatibility
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
// ADDITIONAL ALLURE UTILITIES
// =============================================================================

/**
 * Clean up old allure results before new test run
 */
def cleanAllureResults() {
    sh '''
        echo "🧹 Cleaning previous Allure results..."
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
            echo "📎 Copied ${filePath} to Allure results"
        }
    }
}

/**
 * Set Allure environment properties
 */
def setAllureEnvironment(Map environmentProps) {
    def envContent = environmentProps.collect { key, value ->
        "${key}=${value}"
    }.join('\n')

    writeFile file: 'target/allure-results/environment.properties', text: envContent
    echo "🌍 Set Allure environment properties"
}

/**
 * Add categories for test results
 */
def addAllureCategories() {
    def categoriesContent = '''[
  {
    "name": "Ignored tests",
    "matchedStatuses": ["skipped"]
  },
  {
    "name": "Infrastructure problems",
    "matchedStatuses": ["broken", "failed"],
    "messageRegex": ".*infra.*"
  },
  {
    "name": "Outdated tests",
    "matchedStatuses": ["broken"],
    "traceRegex": ".*FileNotFoundException.*"
  },
  {
    "name": "Product defects",
    "matchedStatuses": ["failed"]
  },
  {
    "name": "Test defects",
    "matchedStatuses": ["broken"]
  }
]'''

    writeFile file: 'target/allure-results/categories.json', text: categoriesContent
    echo "📂 Added Allure categories configuration"
}

return this
