#!/usr/bin/env groovy

/**
 * Test Execution Utilities - Handles Large Test Suites and Command Line Limits
 * Provides solutions for argument list too long errors in Jenkins
 */

// =============================================================================
// CONSTANTS AND CONFIGURATION
// =============================================================================

final class TestExecutionConfig {
    static final List<String> LARGE_TEST_TAGS = [
            'regression-all-services',
            'all-services',
            'regression',
            'api',
            'positive',
            'negative'
    ]

    static final int MAX_COMMAND_LENGTH = 32768  // Conservative limit for most systems
    static final String TEMP_PROPERTIES_FILE = 'cucumber-execution.properties'
}

// =============================================================================
// COMMAND LENGTH MANAGEMENT
// =============================================================================

/**
 * Checks if a tag is likely to cause argument list too long errors
 */
def isLargeTestSuite(String tag) {
    def normalizedTag = tag.toLowerCase().replaceAll('@', '')
    return TestExecutionConfig.LARGE_TEST_TAGS.any { largeTag ->
        normalizedTag.contains(largeTag)
    }
}

/**
 * Estimates command length to prevent argument list errors
 */
def estimateCommandLength(String baseCommand, String tag) {
    // Rough estimation based on typical Maven command structure
    def baseLength = baseCommand.length()
    def tagLength = tag.length()
    def additionalParams = 200  // Buffer for additional Maven parameters

    return baseLength + tagLength + additionalParams
}

/**
 * Executes tests using properties file approach to avoid long command lines
 */
def executeTestsWithPropertiesFile(String tag, def env) {
    echo "🔧 Using properties file approach for large test suite: @${tag}"

    // Create cucumber execution properties file
    def propertiesContent = """
# Cucumber execution configuration
cucumber.filter.tags=@${tag}
cucumber.plugin=pretty,io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm
cucumber.glue=inadigital.api.steps
cucumber.publish.quiet=true
cucumber.features=src/test/resources/features
"""

    writeFile file: TestExecutionConfig.TEMP_PROPERTIES_FILE, text: propertiesContent

    try {
        // Run tests with properties file
        sh """
            echo "📋 Created cucumber execution properties file"
            cat ${TestExecutionConfig.TEMP_PROPERTIES_FILE}
            
            echo "🚀 Executing tests with Maven using properties file approach..."
            mvn test -Dcucumber.options="@src/test/resources/${TestExecutionConfig.TEMP_PROPERTIES_FILE}"
        """
    } catch (Exception e) {
        echo "⚠️ Properties file approach failed, trying alternative method: ${e.getMessage()}"
        executeTestsWithBatchApproach(tag, env)
    } finally {
        // Cleanup properties file
        sh "rm -f ${TestExecutionConfig.TEMP_PROPERTIES_FILE} || true"
    }
}

/**
 * Executes tests in batches to handle large test suites
 */
def executeTestsWithBatchApproach(String tag, def env) {
    echo "🔧 Using batch execution approach for large test suite: @${tag}"

    // Define batch groups for large test suites
    def batchGroups = getBatchGroups(tag)

    def totalBatches = batchGroups.size()
    echo "📊 Executing ${totalBatches} batch(es) for tag: @${tag}"

    for (int i = 0; i < totalBatches; i++) {
        def currentBatch = batchGroups[i]
        def batchNumber = i + 1

        echo "🔄 Running batch ${batchNumber}/${totalBatches}: ${currentBatch.join(' and ')}"

        try {
            def batchTag = currentBatch.size() == 1 ?
                    "@${currentBatch[0]}" :
                    "@${currentBatch.join(' or @')}"

            sh """
                echo "🚀 Executing batch ${batchNumber} with tag: ${batchTag}"
                mvn test -Dcucumber.filter.tags="${batchTag}" -Dtest.batch=${batchNumber}
            """

        } catch (Exception e) {
            echo "⚠️ Batch ${batchNumber} failed: ${e.getMessage()}"
            // Continue with other batches instead of failing completely
            env.BATCH_FAILURES = (env.BATCH_FAILURES ?: 0) + 1
        }
    }

    if (env.BATCH_FAILURES && env.BATCH_FAILURES.toInteger() > 0) {
        echo "⚠️ ${env.BATCH_FAILURES} batch(es) failed during execution"
        currentBuild.result = 'UNSTABLE'
    }
}

/**
 * Gets batch groups based on the tag type
 */
def getBatchGroups(String tag) {
    def normalizedTag = tag.toLowerCase().replaceAll('@', '')

    switch (normalizedTag) {
        case 'regression-all-services':
        case 'all-services':
            return [
                    ['peruriid', 'wizard'],
                    ['sbu', 'digidoc', 'emeterai'],
                    ['inagov', 'inapas', 'inaku'],
                    ['mbg', 'telkomsign']
            ]
        case 'regression':
            return [
                    ['peruriid and @regression'],
                    ['sbu and @regression'],
                    ['inagov and @regression'],
                    ['inapas and @regression'],
                    ['inaku and @regression']
            ]
        case 'positive':
            return [
                    ['peruriid and @positive'],
                    ['sbu and @positive'],
                    ['inagov and @positive'],
                    ['inapas and @positive']
            ]
        case 'negative':
            return [
                    ['peruriid and @negative'],
                    ['sbu and @negative'],
                    ['inagov and @negative']
            ]
        default:
            // For unknown large tags, try to split by service
            return [
                    ['peruriid'],
                    ['sbu'],
                    ['inagov'],
                    ['inapas'],
                    ['inaku'],
                    ['mbg']
            ]
    }
}

// =============================================================================
// ENHANCED TEST EXECUTION
// =============================================================================

/**
 * Main method to execute tests with automatic approach selection
 */
def executeTestsSafely(String tag, def env) {
    def normalizedTag = tag.replaceAll('@', '')

    if (isLargeTestSuite(normalizedTag)) {
        echo "📈 Large test suite detected for tag: @${normalizedTag}"
        echo "🔧 Using specialized execution approach to avoid command line limits"

        // Try properties file approach first, then batch approach as fallback
        try {
            executeTestsWithPropertiesFile(normalizedTag, env)
        } catch (Exception e) {
            echo "⚠️ Properties file approach failed: ${e.getMessage()}"
            echo "🔄 Falling back to batch execution approach"
            executeTestsWithBatchApproach(normalizedTag, env)
        }
    } else {
        echo "📊 Standard test execution for tag: @${normalizedTag}"
        executeTestsStandard(normalizedTag, env)
    }
}

/**
 * Standard test execution for smaller test suites
 */
def executeTestsStandard(String tag, def env) {
    try {
        sh """
            echo "🚀 Executing standard test run with tag: @${tag}"
            mvn test -Dcucumber.filter.tags="@${tag}"
        """
    } catch (Exception e) {
        echo "⚠️ Standard execution failed: ${e.getMessage()}"
        throw e
    }
}

// =============================================================================
// SYSTEM OPTIMIZATION
// =============================================================================

/**
 * Optimizes system environment for large test executions
 */
def optimizeSystemForLargeTests(def env) {
    sh """
        echo "🔧 Optimizing system for large test execution..."
        
        # Increase JVM heap size for Maven
        export MAVEN_OPTS="\${MAVEN_OPTS:-} -Xmx2g -Xms1g"
        
        # Set system properties to handle large argument lists
        export _JAVA_OPTIONS="\${_JAVA_OPTIONS:-} -Djava.awt.headless=true"
        
        # Clean up any previous test artifacts
        rm -rf target/surefire-reports/* || true
        rm -rf target/allure-results/* || true
        
        # Ensure target directories exist
        mkdir -p target/allure-results
        mkdir -p target/surefire-reports
        
        echo "✅ System optimization completed"
    """
}

/**
 * Cleans up after large test execution
 */
def cleanupAfterLargeTests() {
    sh """
        echo "🧹 Cleaning up after large test execution..."
        
        # Remove temporary files
        rm -f ${TestExecutionConfig.TEMP_PROPERTIES_FILE} || true
        rm -f cucumber-execution.properties || true
        
        # Clean up any Maven daemon processes that might be hanging
        pkill -f "maven" || true
        
        echo "✅ Cleanup completed"
    """
}

// =============================================================================
// MONITORING AND REPORTING
// =============================================================================

/**
 * Monitors test execution progress for large suites
 */
def monitorTestProgress(String tag) {
    echo "📊 Monitoring test execution for tag: @${tag}"

    sh """
        echo "📈 Test execution monitoring:"
        echo "Current time: \$(date)"
        echo "Available memory: \$(free -h | grep '^Mem:' | awk '{print \$7}')"
        echo "Disk usage: \$(df -h . | tail -1 | awk '{print \$5}')"
        
        # Check for any running Maven processes
        ps aux | grep -i maven | grep -v grep || echo "No Maven processes found"
    """
}

/**
 * Generates execution summary for large test suites
 */
def generateExecutionSummary(String tag, def env) {
    echo "📋 Generating execution summary for tag: @${tag}"

    def summaryContent = """
# Test Execution Summary

**Tag:** @${tag}
**Execution Type:** ${isLargeTestSuite(tag) ? 'Large Test Suite (Batch/Properties)' : 'Standard'}
**Build Number:** ${env.BUILD_NUMBER}
**Environment:** ${env.TARGET_ENV ?: 'dev'}
**Timestamp:** ${new Date().format("yyyy-MM-dd HH:mm:ss")}

## Execution Details
- **Failed Batches:** ${env.BATCH_FAILURES ?: 0}
- **Build Result:** ${currentBuild.result ?: 'SUCCESS'}

## System Information
- **Jenkins Workspace:** ${env.WORKSPACE}
- **Agent:** ${env.NODE_NAME ?: 'unknown'}
"""

    writeFile file: 'execution-summary.md', text: summaryContent
    archiveArtifacts artifacts: 'execution-summary.md', allowEmptyArchive: true
}

// =============================================================================
// PUBLIC API
// =============================================================================

/**
 * Main entry point for safe test execution
 */
def runTestsSafely(String tag, def env) {
    try {
        // Optimize system for large tests
        optimizeSystemForLargeTests(env)

        // Monitor before execution
        monitorTestProgress(tag)

        // Execute tests with appropriate approach
        executeTestsSafely(tag, env)

        // Generate summary
        generateExecutionSummary(tag, env)

    } catch (Exception e) {
        echo "❌ Test execution failed: ${e.getMessage()}"
        currentBuild.result = 'UNSTABLE'
        throw e
    } finally {
        // Always cleanup
        cleanupAfterLargeTests()
    }
}

return this
