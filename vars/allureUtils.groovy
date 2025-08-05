#!/usr/bin/env groovy

def prepareHistory(String persistentHistoryDir) {
    sh """
        echo "Preparing history files for Allure report"
        mkdir -p target/allure-results/history

        if [ -d "${persistentHistoryDir}" ] && [ "\$(ls -la ${persistentHistoryDir} 2>/dev/null | wc -l)" -gt "3" ]; then
            echo "Copying history from persistent location to target directory"
            cp -f ${persistentHistoryDir}/* target/allure-results/history/ 2>/dev/null || true
        fi
    """
}

def generateReport(String persistentHistoryDir, String allureCommandPath) {
    sh """
        export PATH="${allureCommandPath}:$PATH"
        allure generate target/allure-results -o allure-report --clean

        if [ -d "allure-report/history" ]; then
            echo "Saving history to persistent location"
            mkdir -p ${persistentHistoryDir}
            cp -f allure-report/history/* ${persistentHistoryDir}/ 2>/dev/null || true
        fi
    """
}

return this
