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
 * Executes tests using optimized Maven settings for large test suites
 */
def executeTestsWithOptimizedSettings(String tag, def env) {
    echo "🔧 Using optimized Maven execution for large test suite: @${tag}"

    try {
        // Run tests with optimized JVM settings (Java 17 compatible)
        sh """
            echo "🚀 Executing tests with optimized Maven settings..."
            
            # Set optimized Maven options for large test suites (Java 17 compatible)
            export MAVEN_OPTS="-Xmx4g -Xms1g -XX:MetaspaceSize=512m -XX:MaxMetaspaceSize=1g -XX:+UseG1GC -XX:+UseStringDeduplication"
            
            # Run tests with extended timeout and optimized settings
            timeout 7200s mvn test \\
                -Dcucumber.filter.tags="@${tag}" \\
                -Dcucumber.plugin="pretty,io.qameta.allure.cucumber7jvm.AllureCucumber7Jvm" \\
                -Dcucumber.glue="inadigital.api.steps" \\
                -Dcucumber.publish.quiet=true \\
                -Dlarge.test.suite=true \\
                -Dsurefire.reuseForks=false \\
                -Dsurefire.forkCount=1 \\
                -Dsurefire.argLine="-Xmx2g -Xms512m -XX:MetaspaceSize=256m" \\
                -Dtest.timeout=7200
        """
    } catch (Exception e) {
        echo "⚠️ Optimized execution failed, trying batch execution: ${e.getMessage()}"
        executeTestsWithBatchApproach(tag, env)
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
                
                # Set optimized Maven options for batch execution
                export MAVEN_OPTS="-Xmx2g -Xms512m -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m"
                
                mvn test \\
                    -Dcucumber.filter.tags="${batchTag}" \\
                    -Dtest.batch=${batchNumber} \\
                    -Dsurefire.reuseForks=true \\
                    -Dsurefire.forkCount=1
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

        // Try optimized execution first, then batch approach as fallback
        try {
            executeTestsWithOptimizedSettings(normalizedTag, env)
        } catch (Exception e) {
            echo "⚠️ Optimized execution failed: ${e.getMessage()}"
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
            
            # Set standard Maven options
            export MAVEN_OPTS="-Xmx2g -Xms512m -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m"
            
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
        
        # Set Java 17 compatible JVM options
        export MAVEN_OPTS="-Xmx2g -Xms1g -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m -XX:+UseG1GC"
        export _JAVA_OPTIONS="-Djava.awt.headless=true"
        
        # Clean up any previous test artifacts
        rm -rf target/surefire-reports/* || true
        rm -rf target/allure-results/* || true
        
        # Ensure target directories exist
        mkdir -p target/allure-results
        mkdir -p target/surefire-reports
        
        # Clean up any hanging Maven processes
        pkill -f "maven" || true
        
        echo "✅ System optimization completed"
    """
}

/**
 * Cleans up after large test execution
 */
def cleanupAfterLargeTests() {
    sh """
        echo "🧹 Cleaning up after large test execution..."
        
        # Clean up any Maven daemon processes that might be hanging
        pkill -f "maven" || true
        
        # Clean temporary Maven files
        rm -rf target/.maven-* || true
        
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
        echo "Available memory: \$(free -h | grep '^Mem:' | awk '{print \$7}' || echo 'N/A')"
        echo "Disk usage: \$(df -h . | tail -1 | awk '{print \$5}' || echo 'N/A')"
        
        # Check for any running Maven processes
        ps aux | grep -i maven | grep -v grep || echo "No Maven processes found"
        
        # Check Java version for compatibility
        java -version || echo "Java not found in PATH"
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
**Execution Type:** ${isLargeTestSuite(tag) ? 'Large Test Suite (Optimized/Batch)' : 'Standard'}
**Build Number:** ${env.BUILD_NUMBER}
**Environment:** ${env.TARGET_ENV ?: 'dev'}
**Timestamp:** ${new Date().format("yyyy-MM-dd HH:mm:ss")}

## Execution Details
- **Failed Batches:** ${env.BATCH_FAILURES ?: 0}
- **Build Result:** ${currentBuild.result ?: 'SUCCESS'}

## System Information
- **Jenkins Workspace:** ${env.WORKSPACE}
- **Agent:** ${env.NODE_NAME ?: 'unknown'}
- **Java Version:** Compatible with Java 17
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

/**
 * Simple method for direct Maven execution with optimized settings
 */
def runTestsWithOptimizedMaven(String tag, def env) {
    echo "🚀 Running tests with optimized Maven settings for tag: @${tag}"

    sh """
        # Set Java 17 compatible Maven options
        export MAVEN_OPTS="-Xmx4g -Xms1g -XX:MetaspaceSize=512m -XX:MaxMetaspaceSize=1g -XX:+UseG1GC"
        
        # Run tests with timeout protection
        timeout 7200s mvn test \\
            -Dcucumber.filter.tags="@${tag}" \\
            -Dlarge.test.suite=true \\
            -Dsurefire.reuseForks=false \\
            -Dsurefire.forkCount=1
    """
}

return this
