#!/usr/bin/env groovy

/**
 * OSS (Object Storage Service) Utilities
 * Enhanced version with better path management and error handling
 */

// =============================================================================
// CREDENTIAL SETUP
// =============================================================================

def setupCredentials(String credentialsId) {
    withCredentials([
            usernamePassword(
                    credentialsId: credentialsId,
                    usernameVariable: 'OSS_ACCESS_KEY',
                    passwordVariable: 'OSS_ACCESS_SECRET'
            )
    ]) {
        sh '''
            echo "🔑 Configuring OSS credentials..."
            /var/lib/jenkins/ossutil config -e qa.inadigital.co.id \\
                                       -i "$OSS_ACCESS_KEY" \\
                                       -k "$OSS_ACCESS_SECRET" \\
                                       -L EN
        '''
        echo "✅ OSSUtil configuration completed!"
    }
}

// =============================================================================
// PATH MANAGEMENT
// =============================================================================

def buildRemotePath(String serviceForUrl, String serviceNameForUrl, String buildPath) {
    def sanitizedTag = sanitizeServiceName(serviceForUrl)
    def remotePath = "oss://quality-assurance/${sanitizedTag}/${serviceNameForUrl}/${buildPath}/"

    echo "🗂️ Built remote path: ${remotePath}"
    return remotePath
}

def sanitizeServiceName(String serviceName) {
    return serviceName.toLowerCase()
            .replaceAll("@", "")
            .replaceAll("\\s+(and|or|not)\\s+", "-")
            .replaceAll("[^a-z0-9\\-_]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-\$", "")
}

// =============================================================================
// UPLOAD OPERATIONS
// =============================================================================

def uploadFolder(String localFolder, String remotePath, String endpoint) {
    if (!fileExists(localFolder)) {
        echo "⚠️ Warning: Local folder '${localFolder}' does not exist, skipping upload"
        return false
    }

    try {
        sh """
            echo "📤 Uploading ${localFolder} to ${remotePath} on ${endpoint}..."
            /var/lib/jenkins/ossutil cp -r -u ${localFolder} ${remotePath} --endpoint=${endpoint}
        """
        echo "✅ Upload completed successfully"
        return true
    } catch (Exception e) {
        echo "❌ Upload failed: ${e.getMessage()}"
        throw e
    }
}

def uploadFile(String localFile, String remotePath, String endpoint) {
    if (!fileExists(localFile)) {
        echo "⚠️ Warning: Local file '${localFile}' does not exist, skipping upload"
        return false
    }

    try {
        sh """
            echo "📤 Uploading file ${localFile} to ${remotePath} on ${endpoint}..."
            /var/lib/jenkins/ossutil cp ${localFile} ${remotePath} --endpoint=${endpoint}
        """
        echo "✅ File upload completed successfully"
        return true
    } catch (Exception e) {
        echo "❌ File upload failed: ${e.getMessage()}"
        throw e
    }
}

// =============================================================================
// VERIFICATION OPERATIONS
// =============================================================================

def verifyUpload(String remotePath, String endpoint) {
    try {
        sh """
            echo "🔍 Verifying upload at ${remotePath} on ${endpoint}..."
            /var/lib/jenkins/ossutil ls ${remotePath} --endpoint=${endpoint}
        """
        echo "✅ Upload verification completed successfully"
        return true
    } catch (Exception e) {
        echo "⚠️ Upload verification failed: ${e.getMessage()}"
        return false
    }
}

def checkRemotePathExists(String remotePath, String endpoint) {
    try {
        def result = sh(
                script: "/var/lib/jenkins/ossutil ls ${remotePath} --endpoint=${endpoint}",
                returnStatus: true
        )
        return result == 0
    } catch (Exception e) {
        echo "⚠️ Error checking remote path: ${e.getMessage()}"
        return false
    }
}

// =============================================================================
// CLEANUP OPERATIONS
// =============================================================================

def cleanupOldReports(String basePath, String endpoint, int keepDays = 30) {
    try {
        echo "🧹 Cleaning up reports older than ${keepDays} days..."

        // This would need to be implemented based on your retention policy
        // For now, just log the action
        echo "ℹ️ Cleanup policy: Keep reports for ${keepDays} days"

    } catch (Exception e) {
        echo "⚠️ Cleanup operation failed: ${e.getMessage()}"
    }
}

// =============================================================================
// UTILITY FUNCTIONS
// =============================================================================

def getReportUrl(String baseUrl, String serviceForUrl, String serviceNameForUrl, String buildPath) {
    def sanitizedTag = sanitizeServiceName(serviceForUrl)
    return "${baseUrl}/${sanitizedTag}/${serviceNameForUrl}/${buildPath}/index.html"
}

def listRemoteContents(String remotePath, String endpoint) {
    try {
        def output = sh(
                script: "/var/lib/jenkins/ossutil ls ${remotePath} --endpoint=${endpoint}",
                returnStdout: true
        ).trim()

        echo "📋 Remote contents:\n${output}"
        return output
    } catch (Exception e) {
        echo "❌ Failed to list remote contents: ${e.getMessage()}"
        return ""
    }
}

// =============================================================================
// BATCH OPERATIONS
// =============================================================================

def uploadMultipleFolders(List<String> folders, String baseRemotePath, String endpoint) {
    def results = [:]

    folders.each { folder ->
        def remotePath = "${baseRemotePath}/${folder}/"
        try {
            def success = uploadFolder(folder, remotePath, endpoint)
            results[folder] = success ? 'SUCCESS' : 'FAILED'
        } catch (Exception e) {
            results[folder] = "ERROR: ${e.getMessage()}"
        }
    }

    echo "📊 Batch upload results: ${results}"
    return results
}

/**
 * Enhanced URL generation that includes environment in the path
 */

def getReportUrlWithEnvironment(String baseUrl, String serviceForUrl, String serviceNameForUrl, String buildPath, def env, def params) {
    // Get the effective environment using the same logic as notifications
    def effectiveEnvironment = EnvironmentDetectionHelper.getEffectiveEnvironment(env, params)

    // Sanitize service name
    def sanitizedTag = sanitizeServiceName(serviceForUrl)

    // Include environment in the path structure
    def environmentSuffix = effectiveEnvironment?.toLowerCase() ?: 'dev'
    def enhancedServiceName = "${sanitizedTag}-${environmentSuffix}"

    echo "🔗 Building URL with environment: ${enhancedServiceName}"

    return "${baseUrl}/${enhancedServiceName}/${serviceNameForUrl}/${buildPath}/index.html"
}

/**
 * Enhanced remote path building that includes environment
 */
def buildRemotePathWithEnvironment(String serviceForUrl, String serviceNameForUrl, String buildPath, def env, def params) {
    // Get the effective environment
    def effectiveEnvironment = EnvironmentDetectionHelper.getEffectiveEnvironment(env, params)
    def environmentSuffix = effectiveEnvironment?.toLowerCase() ?: 'dev'

    // Sanitize service name and add environment
    def sanitizedTag = sanitizeServiceName(serviceForUrl)
    def enhancedServiceName = "${sanitizedTag}-${environmentSuffix}"

    def remotePath = "oss://quality-assurance/${enhancedServiceName}/${serviceNameForUrl}/${buildPath}/"

    echo "🗂️ Built environment-aware remote path: ${remotePath}"
    return remotePath
}

return this
