#!/usr/bin/env groovy

/**
 * OBS (Object Storage Service) Utilities
 * Huawei Cloud OBS integration for report storage
 * Works alongside existing OSS (Alibaba Cloud) implementation
 */

// =============================================================================
// CREDENTIAL SETUP
// =============================================================================

def setupCredentials(String credentialsId) {
    withCredentials([
            usernamePassword(
                    credentialsId: credentialsId,
                    usernameVariable: 'OBS_ACCESS_KEY',
                    passwordVariable: 'OBS_ACCESS_SECRET'
            )
    ]) {
        sh '''
            echo "🔑 Configuring OBS credentials..."
            /var/lib/jenkins/obsutil config -i="$OBS_ACCESS_KEY" \\
                                       -k="$OBS_ACCESS_SECRET" \\
                                       -e=obs.ap-southeast-3.myhuaweicloud.com
        '''
        echo "✅ OBSUtil configuration completed!"
    }
}

// =============================================================================
// PATH MANAGEMENT
// =============================================================================

def buildRemotePath(String serviceForUrl, String serviceNameForUrl, String buildPath) {
    def sanitizedTag = sanitizeServiceName(serviceForUrl)
    def remotePath = "obs://quality-assurance/${sanitizedTag}/${serviceNameForUrl}/${buildPath}/"

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
            /var/lib/jenkins/obsutil cp -r -f ${localFolder} ${remotePath}
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
            /var/lib/jenkins/obsutil cp ${localFile} ${remotePath}
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
            /var/lib/jenkins/obsutil ls ${remotePath}
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
                script: "/var/lib/jenkins/obsutil ls ${remotePath}",
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
                script: "/var/lib/jenkins/obsutil ls ${remotePath}",
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

return this
