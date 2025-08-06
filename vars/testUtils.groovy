#!/usr/bin/env groovy

/**
 * Test Utilities - Simplified and Centralized
 * This file now focuses on core test utility functions while
 * delegating specialized functionality to dedicated managers
 */

// =============================================================================
// BACKWARD COMPATIBILITY METHODS
// =============================================================================

def determineTagToUse(String jobName, String defaultTag) {
    // Delegate to dataManager for consistency
    return dataManager.determineEffectiveTag(jobName, defaultTag)
}

def collectStatistics() {
    // Delegate to reportManager for consistency
    return reportManager.collectAllureStatistics()
}

def storeStatistics(def stats, def env) {
    // Delegate to reportManager for consistency
    reportManager.storeApiStatistics(stats, env)
}

def collectWebTestStatisticsFromJSON() {
    // Delegate to reportManager for consistency
    return reportManager.collectPlaywrightStatistics()
}

def storeWebTestStatistics(def testStats, def env) {
    // Delegate to reportManager for consistency
    reportManager.storeWebStatistics(testStats, env)
}

def collectWebTestStatistics() {
    // Delegate to reportManager for consistency
    return reportManager.collectPlaywrightStatistics()
}

// =============================================================================
// GOOGLE SHEETS INTEGRATION (Backward Compatibility)
// =============================================================================

def mapTagToSheets(String tagToUse) {
    // Delegate to dataManager for consistency
    return dataManager.mapTagToSheets(tagToUse)
}

def getEffectiveSheetNames(String tagToUse, String overrideSheetName = null) {
    // Delegate to dataManager for consistency
    return dataManager.getEffectiveSheetNames(tagToUse, overrideSheetName)
}

def loadTestDataFromSheets(List<String> sheetNames, String spreadsheetId = null) {
    // Delegate to dataManager for consistency
    dataManager.loadTestDataFromSheets(sheetNames, spreadsheetId)
}

def loadTestDataForService(String serviceTag) {
    // Delegate to dataManager for consistency
    dataManager.loadTestDataForService(serviceTag)
}

// =============================================================================
// UTILITY FUNCTIONS
// =============================================================================

/**
 * Generate a unique build identifier
 */
def generateBuildId() {
    def timestamp = new Date().format("yyyyMMdd-HHmmss", TimeZone.getTimeZone('Asia/Jakarta'))
    return "${timestamp}-${env.BUILD_NUMBER}"
}

/**
 * Check if a file or directory exists
 */
def fileOrDirExists(String path) {
    return fileExists(path)
}

/**
 * Safe string trimming
 */
def safeTrim(String value) {
    return value?.trim() ?: ''
}

/**
 * Parse boolean from string safely
 */
def parseBoolean(def value) {
    if (value instanceof Boolean) return value
    if (value instanceof String) {
        return value.toLowerCase() in ['true', 'yes', '1', 'on']
    }
    return false
}

/**
 * Get environment variable with default
 */
def getEnvVar(String varName, String defaultValue = '') {
    return env[varName] ?: defaultValue
}

/**
 * Create a safe filename from string
 */
def createSafeFilename(String input) {
    return input.toLowerCase()
            .replaceAll("[^a-z0-9\\-_]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-\$", "")
}

// =============================================================================
// TEST EXECUTION HELPERS
// =============================================================================

/**
 * Check if tests should be executed based on conditions
 */
def shouldExecuteTests(def params, def env) {
    // Add logic to determine if tests should run
    // Based on branch, environment, time, etc.
    return true
}

/**
 * Validate test parameters
 */
def validateTestParameters(def params) {
    def errors = []

    if (!params.TARGET_ENV) {
        errors.add("TARGET_ENV is required")
    }

    if (!params.QA_SERVICE) {
        errors.add("QA_SERVICE is required")
    }

    if (errors.size() > 0) {
        error("Parameter validation failed: ${errors.join(', ')}")
    }

    return true
}

/**
 * Get test timeout based on service type
 */
def getTestTimeout(String serviceType, String testType = 'default') {
    def timeouts = [
            'api': [
                    'default': 30,
                    'smoke': 15,
                    'regression': 60
            ],
            'web': [
                    'default': 120,
                    'smoke': 30,
                    'regression': 180
            ],
            'mobile': [
                    'default': 90,
                    'smoke': 30,
                    'regression': 150
            ]
    ]

    def serviceTimeouts = timeouts[serviceType.toLowerCase()] ?: timeouts['api']
    return serviceTimeouts[testType.toLowerCase()] ?: serviceTimeouts['default']
}

// =============================================================================
// REPORTING HELPERS
// =============================================================================

/**
 * Format duration for display
 */
def formatDuration(long durationMs) {
    def duration = durationMs / 1000
    def hours = (duration / 3600).intValue()
    def minutes = ((duration % 3600) / 60).intValue()
    def seconds = (duration % 60).intValue()

    if (hours > 0) {
        return "${hours}h ${minutes}m ${seconds}s"
    } else if (minutes > 0) {
        return "${minutes}m ${seconds}s"
    } else {
        return "${seconds}s"
    }
}

/**
 * Get test summary string
 */
def getTestSummary(def testStats) {
    def total = testStats.total ?: 0
    def passed = testStats.passed ?: 0
    def failed = testStats.failed ?: 0
    def skipped = testStats.skipped ?: 0

    def successRate = total > 0 ? (passed * 100 / total).intValue() : 100

    return "Tests: ${total}, Passed: ${passed}, Failed: ${failed}, Skipped: ${skipped}, Success Rate: ${successRate}%"
}

// =============================================================================
// ERROR HANDLING
// =============================================================================

/**
 * Handle test execution errors gracefully
 */
def handleTestError(Exception error, String context = '') {
    def errorMessage = context ? "${context}: ${error.getMessage()}" : error.getMessage()
    echo "❌ Test Error: ${errorMessage}"

    // Log stack trace for debugging
    echo "Stack trace: ${error.getStackTrace().join('\n')}"

    // Determine if this should fail the build or just mark as unstable
    if (error.getMessage().contains('timeout') || error.getMessage().contains('unstable')) {
        currentBuild.result = 'UNSTABLE'
    } else {
        throw error
    }
}

/**
 * Retry operation with exponential backoff
 */
def retryWithBackoff(Closure operation, int maxAttempts = 3, int baseDelaySeconds = 2) {
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        try {
            return operation()
        } catch (Exception e) {
            if (attempt == maxAttempts) {
                throw e
            }

            def delaySeconds = baseDelaySeconds * Math.pow(2, attempt - 1)
            echo "⚠️ Attempt ${attempt} failed, retrying in ${delaySeconds} seconds: ${e.getMessage()}"
            sleep(delaySeconds as Integer)
        }
    }
}

return this
