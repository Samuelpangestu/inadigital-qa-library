#!/usr/bin/env groovy

/**
 * Test Data Management Utilities
 * Handles Google Sheets integration, test data loading, and tag mapping
 */

// =============================================================================
// TAG DETERMINATION
// =============================================================================

def determineEffectiveTag(String jobName, String defaultTag, def params = null) {
    def jobNameLower = jobName.toLowerCase()

    // Job name based tag determination
    if (jobNameLower.contains('inagov')) return 'inagov'
    if (jobNameLower.contains('inapas')) return 'inapas'
    if (jobNameLower.contains('inaku')) return 'inaku'
    if (jobNameLower.contains('sbu')) return 'sbu'
    if (jobNameLower.contains('emeterai-smoke')) return 'emeterai-smoke'
    if (jobNameLower.contains('mbg')) return 'mbg'
    if (jobNameLower.contains('peruriid')) return 'peruriid'
    if (jobNameLower.contains('wizard')) return 'wizard'
    if (jobNameLower.contains('telkomsign')) return 'telkomsign'
    if (jobNameLower.contains('metel')) return 'metel'
    if (jobNameLower.contains('digitrust')) return 'digitrust'
    if (jobNameLower.contains('digidoc-dashboard-cmp')) return 'digidoc-dashboard-cmp'

    // Web-specific job patterns
    if (jobNameLower.contains('perisai-ultimate')) return 'perisai-ultimate'
    if (jobNameLower.contains('change-password')) return 'change-password'
    if (jobNameLower.contains('login-success')) return 'login-success'
    if (jobNameLower.contains('regression-all-web')) return 'regression'

    // For pipelines with custom tag logic
    if (params && params.USE_CUSTOM_TAG) {
        if (params.CUSTOM_TAG?.trim()) {
            def tag = params.CUSTOM_TAG.trim()
            return tag.startsWith('@') ? tag.substring(1) : tag
        }
    }

    return defaultTag
}

def setupTagMetadata(def env, def params) {
    def tagToUse
    def tagSource = "QA_SERVICE"

    if (params.USE_CUSTOM_TAG && params.CUSTOM_TAG?.trim()) {
        tagToUse = params.CUSTOM_TAG.trim()
        tagSource = "CUSTOM_TAG"

        if (tagToUse.startsWith('@')) {
            tagToUse = tagToUse.substring(1)
        }
    } else if (params.USE_CUSTOM_TAG) {
        // USE_CUSTOM_TAG is true but no CUSTOM_TAG provided, fallback to job-based logic
        tagToUse = determineEffectiveTag(env.JOB_NAME, params.QA_SERVICE, params)
        tagSource = "TESTUTILS_FALLBACK"
    } else {
        // Standard logic - determine from job name or use QA_SERVICE
        tagToUse = determineEffectiveTag(env.JOB_NAME, params.QA_SERVICE, params)
        tagSource = "TESTUTILS_LIBRARY"
    }

    env.EFFECTIVE_QA_SERVICE = tagToUse
    env.TAG_SOURCE = tagSource

    return [tag: tagToUse, source: tagSource]
}

// =============================================================================
// GOOGLE SHEETS MAPPING
// =============================================================================

/**
 * Maps a tag/service to corresponding Google Sheet names
 * @param tagToUse The service tag to map
 * @return List of sheet names to load data from
 */
def mapTagToSheets(String tagToUse) {
    def sheetMapping = [
            'peruriid'             : ['PERURIID'],
            'external-iam'         : ['PERURIID'],
            'internal-iam'         : ['PERURIID'],
            'wizard'               : ['PERURIID'],
            'sbu'                  : ['SBU'],
            'digidoc-dashboard-cmp': ['SBU'],
            'digitrust'            : ['SBU'],
            'cmp'                  : ['SBU'],
            'emeterai'             : ['SBU'],
            'metel'                : ['SBU'],
            'mbg'                  : ['MBG'],
            'inagov'               : ['INAGOV'],
            'inapas'               : ['INAPAS'],
            'inaku'                : ['INAKU'],
            'telkomsign'           : ['TELKOMSIGN'],
            // Multi-service tags
            'regression'           : ['PERURIID', 'SBU', 'INAGOV', 'INAPAS', 'INAKU', 'TELKOMSIGN'],
            'positive'             : ['PERURIID', 'SBU', 'INAGOV', 'INAPAS', 'INAKU', 'TELKOMSIGN'],
            'negative'             : ['PERURIID', 'SBU', 'INAGOV', 'INAPAS', 'INAKU', 'TELKOMSIGN'],
            'login'                : ['PERURIID', 'SBU', 'INAGOV', 'INAPAS', 'INAKU', 'TELKOMSIGN'],
            'smoke'                : ['PERURIID', 'SBU', 'INAGOV', 'INAPAS', 'INAKU', 'TELKOMSIGN'],
            'api'                  : ['PERURIID', 'SBU', 'INAGOV', 'INAPAS', 'INAKU', 'TELKOMSIGN'],
            // Web-specific mappings
            'perisai-ultimate'     : ['PERURIID'],
            'change-password'      : ['PERURIID'],
            'login-success'        : ['PERURIID'],
            'test'                 : ['PERURIID'],
            'regression-all-web'   : ['PERURIID', 'SBU']
    ]

    def normalizedTag = tagToUse.toLowerCase().trim()

    // Find matching keys using contains logic
    for (def entry : sheetMapping) {
        if (normalizedTag.contains(entry.key)) {
            echo "📋 Tag '${normalizedTag}' mapped to sheets: ${entry.value}"
            return entry.value
        }
    }

    // Fallback: return all sheets when no tag is found in the mapping
    def fallbackSheets = ['PERURIID', 'SBU', 'INAGOV', 'INAPAS', 'INAKU', 'SBU_WEB', 'TELKOMSIGN']
    echo "📋 Tag '${normalizedTag}' not found in mapping, using fallback sheets: ${fallbackSheets}"
    return fallbackSheets
}

/**
 * Get effective sheet names based on tag and optional override
 * @param tagToUse The service tag determined from job
 * @param overrideSheetName Optional sheet name override from parameters
 * @return List of sheet names to use
 */
def getEffectiveSheetNames(String tagToUse, String overrideSheetName = null) {
    if (overrideSheetName?.trim()) {
        // If override is provided, split by comma and trim each name
        def sheets = overrideSheetName.split(',').collect { it.trim() }.findAll { it }
        echo "📋 Using override sheet names: ${sheets}"
        return sheets
    } else {
        // Use the mapping strategy
        return mapTagToSheets(tagToUse)
    }
}

// =============================================================================
// GOOGLE SHEETS DATA LOADING
// =============================================================================

/**
 * Load test data from Google Sheets for specified sheet names
 * @param sheetNames List of sheet names to load data from
 * @param spreadsheetId Google Sheets spreadsheet ID (optional, uses default from TestData class)
 */
def loadTestDataFromSheets(List<String> sheetNames, String spreadsheetId = null) {
    echo "📊 Loading test data from ${sheetNames.size()} sheet(s): ${sheetNames.join(', ')}"

    for (String sheetName : sheetNames) {
        echo "📄 Processing sheet: ${sheetName}"

        try {
            // Clear any existing SHEET environment variable and set the new one
            sh """
                sed -i '/^SHEET=/d' .env || true
                echo "SHEET=${sheetName}" >> .env
                
                # Set spreadsheet ID if provided
                ${spreadsheetId ? "sed -i '/^SPREADSHEET_ID=/d' .env || true" : ""}
                ${spreadsheetId ? "echo 'SPREADSHEET_ID=${spreadsheetId}' >> .env" : ""}
                
                # Load test data using Maven
                export MAVEN_OPTS="-Xmx1024m -Dfile.encoding=UTF-8"
                mvn exec:java -Dexec.mainClass="inadigital.test_data.TestData" -q
            """

            echo "✅ Successfully loaded data from sheet: ${sheetName}"

        } catch (Exception e) {
            echo "⚠️ Warning: Failed to load data from sheet '${sheetName}': ${e.getMessage()}"
            // Continue with other sheets instead of failing completely
        }
    }

    echo "📊 All test data loading completed"
}

/**
 * Simplified method for backward compatibility
 * Loads test data based on the determined tag
 */
def loadTestDataForService(String serviceTag) {
    def sheetNames = mapTagToSheets(serviceTag)
    loadTestDataFromSheets(sheetNames)
}

/**
 * Setup Google Sheets configuration for API tests
 */
def setupGoogleSheetsForApi(def params, def env) {
    def tagResult = setupTagMetadata(env, params)
    def effectiveSheetNames = getEffectiveSheetNames(tagResult.tag, params.SHEET_NAME)
    env.EFFECTIVE_SHEET_NAMES = effectiveSheetNames.join(',')

    def displayTag = params.USE_CUSTOM_TAG ? "@${tagResult.tag}" : params.QA_SERVICE
    currentBuild.description = "Tag: @${tagResult.tag} (${tagResult.source}) | Sheets: ${effectiveSheetNames.join(', ')}"

    echo "🏷️ Tag: @${tagResult.tag} (${tagResult.source}) | Sheets: ${effectiveSheetNames.join(', ')}"

    return effectiveSheetNames
}

// =============================================================================
// ENVIRONMENT SETUP
// =============================================================================

/**
 * Setup environment with Google Sheets configuration
 */
def setupEnvironmentWithSheets(String credentialsId, def params) {
    withCredentials([
            file(credentialsId: credentialsId, variable: 'SECRET_FILE'),
            file(credentialsId: "qa-google-service-account-key", variable: 'SERVICE_ACCOUNT_KEY')
    ]) {
        sh '''
            # Copy base .env file
            if [ -f "$SECRET_FILE" ]; then
                cat "$SECRET_FILE" > .env
            else
                touch .env
            fi

            # Add Google Sheets configuration
            echo "" >> .env
            echo "SPREADSHEET_ID=${SPREADSHEET_ID}" >> .env

            # Setup Google Service Account key
            if [ -f "$SERVICE_ACCOUNT_KEY" ]; then
                cat "$SERVICE_ACCOUNT_KEY" > key.json
                chmod 600 key.json
            else
                echo "ERROR: SERVICE_ACCOUNT_KEY file not found"
                exit 1
            fi
        '''
    }
}

/**
 * Load test data for the current pipeline
 */
def loadCurrentPipelineData(def env) {
    def sheetsToProcess = env.EFFECTIVE_SHEET_NAMES.split(',').collect { it.trim() }
    loadTestDataFromSheets(sheetsToProcess, env.SPREADSHEET_ID)
}

// =============================================================================
// WEB TEST DATA HANDLING
// =============================================================================

/**
 * Setup test data for web tests (simpler approach)
 */
def setupWebTestData(String tagToUse) {
    echo "📊 Setting up web test data for tag: ${tagToUse}"

    // Web tests typically don't need Google Sheets data loading
    // but we can extend this if needed

    def relevantSheets = mapTagToSheets(tagToUse)
    echo "📋 Relevant sheets for web tests: ${relevantSheets.join(', ')}"

    return relevantSheets
}

// =============================================================================
// MOBILE TEST DATA HANDLING
// =============================================================================

/**
 * Setup test data for mobile tests
 */
def setupMobileTestData(String tagToUse) {
    echo "📱 Setting up mobile test data for tag: ${tagToUse}"

    def relevantSheets = mapTagToSheets(tagToUse)
    echo "📋 Relevant sheets for mobile tests: ${relevantSheets.join(', ')}"

    return relevantSheets
}

return this
