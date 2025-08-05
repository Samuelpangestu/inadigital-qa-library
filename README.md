# INADigital QA Library

A collection of reusable Jenkins pipeline utilities specifically designed for API test automation at INADigital.

## Overview

This shared library provides modular, maintainable functions for Jenkins pipelines that handle test execution, reporting, and notifications for INADigital's QA processes. It works alongside the existing `shared-library` maintained by DevOps.

## Repository Structure

```
jenkins-qa-library/
├── vars/                   # Global variables/functions
│   ├── allureUtils.groovy  # Allure report management
│   ├── notificationUtils.groovy # Notification systems
│   ├── ossUtils.groovy     # Object storage operations
│   └── testUtils.groovy    # Test execution helpers
├── src/                    # (Optional) More complex classes 
├── resources/              # (Optional) Non-Groovy files
└── README.md               # This documentation
```

## Installation

### 1. Create Repository

Create a new Git repository with the structure above, accessible to your Jenkins instance.

### 2. Register in Jenkins

In Jenkins:
1. Navigate to `Manage Jenkins` > `System` > `Global Pipeline Libraries`
2. Add a new library:
   - Name: `qa-library`
   - Default version: `main`
   - Retrieval method: `Modern SCM`
   - Select Git and provide your repository URL
   - Credentials (if needed)

## Usage

### 1. Import the Library

In your Jenkinsfile, import both the DevOps shared library and the QA library:

```groovy
@Library(['shared-library', 'qa-library'])_
```

### 2. Use the Utilities

Call the functions from your pipeline:

```groovy
pipeline {
    // ...
    stages {
        stage('Run Tests') {
            steps {
                script {
                    def tagToUse = testUtils.determineTagToUse(env.JOB_NAME, params.QA_SERVICE)
                    // ...
                }
            }
        }
        // ...
    }
}
```

## Available Modules

### allureUtils

Functions for working with Allure reports:

| Function | Description |
|----------|-------------|
| `prepareHistory(persistentHistoryDir)` | Prepares Allure history before report generation |
| `generateReport(persistentHistoryDir, allureCommandPath)` | Generates the Allure report |

Example:
```groovy
allureUtils.prepareHistory("/var/lib/jenkins/allure-history/inagov")
allureUtils.generateReport("/var/lib/jenkins/allure-history/inagov", "/path/to/allure/bin")
```

### testUtils

Utilities for test execution and statistics:

| Function | Description |
|----------|-------------|
| `determineTagToUse(jobName, defaultTag)` | Auto-detects tag based on job name |
| `collectStatistics()` | Collects test execution statistics from Allure data |
| `storeStatistics(stats, env)` | Stores statistics in environment variables |

Example:
```groovy
def tagToUse = testUtils.determineTagToUse(env.JOB_NAME, "regression-all")
def stats = testUtils.collectStatistics()
testUtils.storeStatistics(stats, env)
```

### ossUtils

Handles operations with object storage:

| Function | Description |
|----------|-------------|
| `setupCredentials(credentialsId)` | Configures OSS credentials |
| `uploadFolder(localFolder, remotePath, endpoint)` | Uploads folder to OSS |
| `verifyUpload(remotePath, endpoint)` | Verifies successful upload |

Example:
```groovy
ossUtils.setupCredentials('alicloud-oss-qa')
ossUtils.uploadFolder("allure-report", "oss://bucket/path/", "oss-ap-southeast-5.aliyuncs.com")
```

### notificationUtils

Handles notifications to various platforms:

| Function | Description |
|----------|-------------|
| `sendGoogleChatNotification(buildStatus, reportUrl, commitId, env, params)` | Sends formatted notification to Google Chat |
| Various helper methods for notification formatting | Creates sections of the notification |

Example:
```groovy
notificationUtils.sendGoogleChatNotification(
    'SUCCESS', 
    'https://reports.example.com/report1', 
    'abc1234',
    env,
    params
)
```

## Maintenance

### Adding New Functions

1. Create or modify the appropriate .groovy file in the `vars` directory
2. Implement your function with proper documentation
3. Return `this` at the end of the file to allow method chaining
4. Commit and push to the repository

### Best Practices

1. Keep functions focused and single-purpose
2. Add descriptive comments
3. Use consistent parameter naming
4. Return results rather than modifying global state where possible
5. Handle errors gracefully
6. Use type hints for parameters when possible

## Example Implementations

See the implementation section below for code examples of each utility file.