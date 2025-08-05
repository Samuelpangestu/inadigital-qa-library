#!/usr/bin/env groovy

def setupCredentials(String credentialsId) {
    withCredentials([
            usernamePassword(
                    credentialsId: credentialsId,
                    usernameVariable: 'OSS_ACCESS_KEY',
                    passwordVariable: 'OSS_ACCESS_SECRET'
            )
    ]) {
        sh '''
            /var/lib/jenkins/ossutil config -e qa.inadigital.co.id \
                                       -i "$OSS_ACCESS_KEY" \
                                       -k "$OSS_ACCESS_SECRET" \
                                       -L EN
        '''
        echo "OSSUtil configuration completed!"
    }
}

def uploadFolder(String localFolder, String remotePath, String endpoint) {
    sh """
        echo "Uploading ${localFolder} to ${remotePath} on ${endpoint}..."
        /var/lib/jenkins/ossutil cp -r -u ${localFolder} ${remotePath} --endpoint=${endpoint}
    """
}

def verifyUpload(String remotePath, String endpoint) {
    sh """
        echo "Verifying upload at ${remotePath} on ${endpoint}..."
        /var/lib/jenkins/ossutil ls ${remotePath} --endpoint=${endpoint}
    """
}

return this
