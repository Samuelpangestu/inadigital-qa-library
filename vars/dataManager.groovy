#!/usr/bin/env groovy
/**
 * Test Data Management Utilities (API, Web, Mobile)
 * Handles Google Sheets integration, test data loading, and tag mapping
 * Used by Jenkins pipelines for different automation types
 */

// =============================================================================
// TAG DETERMINATION
// =============================================================================

def determineEffectiveTag(String jobName, String defaultTag, def params = null) {
    def jobNameLower = jobName.toLowerCase()

    if (jobNameLower.contains('inagov')) return 'inagov'
    if (jobNameLower.contains('inapas')) return 'inapas'
    if (jobNameLower.contains('inaku')) return 'inaku'
    if (jobNameLower.contains('sbu')) return 'sbu'
    if (jobNameLower.contains('emeterai-smoke-staging')) return 'emeterai-smoke-staging'
    if (jobNameLower.contains('emeterai-smoke-prod')) return 'emeterai-smoke-prod'
    if (jobNameLower.contains('mbg')) return 'mbg'
    if (jobNameLower.contains('peruriid')) return 'peruriid'
    if (jobNameLower.contains('wizard')) return 'wizard'
    if (jobNameLower.contains('telkomsign-daily-dev')) return 'telkomsign'
    if (jobNameLower.contains('telkomsign-smoke-dev')) return 'telkomsign-smoke-dev'
    if (jobNameLower.contains('metel')) return 'metel'
    if (jobNameLower.contains('digitrust')) return 'digitrust'
    if (jobNameLower.contains('digidoc-dashboard-cmp')) return 'digidoc-dashboard-cmp'
    if (jobNameLower.contains('emudhra')) return 'emudhra'

    // Web-specific job patterns
    if (jobNameLower.contains('web-peruriid')) return 'web-peruriid'
    if (jobNameLower.contains('perisai-digidoc')) return 'perisai-digidoc'
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
            'emudhra'              : ['SBU'],
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
            'perisai-digidoc'      : ['PERISAI-DIGIDOC'],
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
    def fallbackSheets = ['PERURIID', 'SBU', 'INAGOV', 'INAPAS', 'INAKU', 'PERISAI-DIGIDOC', 'TELKOMSIGN', 'MBG']
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
// LOAD TEST DATA FROM SHEETS
// =============================================================================

/**
 * Load test data from Google Sheets for specified sheet names
 * @param sheetNames List of sheet names to load data from
 * @param spreadsheetId Google Sheets spreadsheet ID (optional, uses default from TestData class)
 */
def loadTestDataFromSheets(List<String> sheetNames, String spreadsheetId, String automationType) {
    echo "📊 Loading test data from ${sheetNames.size()} sheet(s): ${sheetNames.join(', ')}"
    echo "🔧 Automation Type: ${automationType.toUpperCase()}"

    for (String sheetName : sheetNames) {
        echo "📄 Processing sheet: ${sheetName}"

        try {
            sh """
                #!/bin/bash
                set -e
                sed -i '/^SHEET=/d' .env || true
                echo "SHEET=${sheetName}" >> .env

                if [ -n "${spreadsheetId}" ]; then
                    sed -i '/^SPREADSHEET_ID=/d' .env || true
                    echo "SPREADSHEET_ID=${spreadsheetId}" >> .env
                fi
            """

            switch (automationType.toLowerCase()) {
                case 'api':
                    echo "🔌 Loading test data for API automation"
                    sh """
                        #!/bin/bash
                        set -e
                        export MAVEN_OPTS="-Xmx1024m -Dfile.encoding=UTF-8"
                        mvn exec:java -Dexec.mainClass='inadigital.test_data.TestData' -q
                    """
                    break

                case 'web':
                    echo "🌐 Syncing environment for Playwright web automation"
                    sh """
                        #!/bin/bash
                        set -e
                        if command -v pnpm &> /dev/null; then
                            pnpm sync:env
                        else
                            echo "⚠️ pnpm not found, using npm fallback"
                            npm run sync:env
                        fi
                    """
                    break

                case 'mobile':
                    echo "📱 Loading test data for Mobile automation"
                    sh """
                        #!/bin/bash
                        set -e
                        if [ -f "gradlew" ]; then
                            ./gradlew prepareTestData
                        elif command -v npm &> /dev/null; then
                            npm run sync:env
                        else
                            echo "⚠️ No supported mobile build tool found"
                        fi
                    """
                    break

                default:
                    echo "⚠️ Unknown automation type: ${automationType}. Skipping..."
                    break
            }

        } catch (Exception e) {
            echo "⚠️ Failed to load data from sheet '${sheetName}': ${e.getMessage()}"
        }
    }

    echo "✅ All test data loading completed for ${automationType.toUpperCase()} automation"
}

return this
