#!/bin/bash

# =================================================================
# Notarize and Staple the app manually (Final Version)
# This script uses Apple's latest notarization tool and a robust workflow.
# It's fully automated and uses environment variables from direnv.
#
# Usage: ./scripts/notarize.sh [--intel]
#        --intel : Build and notarize for Intel Mac (optional)
# =================================================================

set -e # Exit immediately if a command exits with a non-zero status.

# Process command line arguments
BUILD_INTEL=false
SHOULD_CLEAN=false
for arg in "$@"; do
  case $arg in
    --intel)
      BUILD_INTEL=true
      shift
      ;;
    --clean)
      SHOULD_CLEAN=true
      shift
      ;;
    *)
      # Ignore unknown options
      shift
      ;;
  esac
done

# Display guidance for Intel Mac build
if [ "$BUILD_INTEL" = true ]; then
    echo "🚀 Starting notarization process for Intel Mac (using notarytool)..."
else
    echo "🚀 Starting notarization process for Apple Silicon Mac (using notarytool)..."
    echo "   For Intel Mac build, use: ./scripts/notarize.sh --intel"
    echo "   Or use: ./scripts/build-intel-mac.sh (after Apple Silicon build)"
    echo "   To clean before building, add: --clean"
    echo ""
    
    # Only run clean if explicitly requested
    if [ "$SHOULD_CLEAN" = true ]; then
        echo "🧹 Cleaning previous build as requested..."
        ./gradlew clean
        echo "✅ Clean completed"
    fi
fi

# --- Configuration ---
APP_NAME="Cursor Talk to Figma desktop"
# Use the path from INTEL_APP environment variable if set (when called from build-intel-mac.sh)
# 그렇지 않으면 기본 경로 사용
if [ -n "$INTEL_APP" ] && [ -d "$INTEL_APP" ]; then
    echo "ℹ️ Using Intel build app path: $INTEL_APP"
    APP_PATH="$INTEL_APP"
    IS_INTEL=true
else
    # The .app bundle is located in the 'app' directory, not 'dmg'
    APP_PATH="app/build/compose/binaries/main/app/${APP_NAME}.app"
    IS_INTEL=false
    echo "ℹ️ Using default app path: $APP_PATH"
fi

# Change directory name for Intel version
if [ "$IS_INTEL" = true ]; then
    BUILD_DIR="app/build/intel_notarization"
else
    BUILD_DIR="app/build/manual_notarization"
fi

ZIP_PATH="${BUILD_DIR}/${APP_NAME}.zip"
APP_VERSION=$(cd app && ../gradlew -q printVersion)

# Check if required environment variables are set
if [ -z "$APPLE_ID" ] || [ -z "$APPLE_PASSWORD" ] || [ -z "$APPLE_TEAM_ID" ]; then
  echo "❌ Error: Required environment variables (APPLE_ID, APPLE_PASSWORD, APPLE_TEAM_ID) are not set."
  exit 1
fi

echo "📋 Configuration:"
echo "   - App Version: $APP_VERSION"
echo "   - App Path: $APP_PATH"
echo "   - Team ID: $APPLE_TEAM_ID"
echo ""

# --- 1. Check if app exists or build it if needed ---
if [ -d "$APP_PATH" ]; then
    echo "🔍 [Step 1/5] App already exists at: $APP_PATH. Skipping build step."
else
    echo "🔨 [Step 1/5] Building the app using './gradlew createDistributable'..."
    # We ONLY run createDistributable which creates the .app, not packageDmg
    ./gradlew createDistributable
fi

if [ ! -d "$APP_PATH" ]; then
    echo "❌ Error: App not found at path: $APP_PATH. The build might have failed or the path changed."
    exit 1
fi
echo "✅ App built and signed successfully at: $APP_PATH"

# --- 2. Verify code signing before notarization ---
echo "🔍 [Step 2/5] Verifying code signing..."
/usr/bin/codesign -vvv --deep --strict "$APP_PATH"
echo "✅ Code signing verification passed"

# --- 3. Create a zip file for notarization using ditto ---
echo "📦 [Step 3/5] Creating zip file for notarization..."
mkdir -p "$BUILD_DIR"
/usr/bin/ditto -c -k --sequesterRsrc --keepParent "$APP_PATH" "$ZIP_PATH"
echo "✅ Zip file created at: $ZIP_PATH"

# --- 4. Upload and wait for notarization ---
echo "⬆️  [Step 4/5] Submitting app to Apple for notarization with 'notarytool'..."
set +e # Temporarily disable exit-on-error to capture notarytool's output
SUBMISSION_ID=$(xcrun notarytool submit "$ZIP_PATH" \
    --apple-id "$APPLE_ID" \
    --password "$APPLE_PASSWORD" \
    --team-id "$APPLE_TEAM_ID" \
    --wait \
    --output-format json | grep -o '"id":"[^"]*"' | cut -d'"' -f4)

# Check for submission ID
if [ -z "$SUBMISSION_ID" ]; then
    echo "❌ Notarization submission failed! Could not get submission ID."
    exit 1
fi

echo "   Submission ID: $SUBMISSION_ID"

# Check notarization status
NOTARY_STATUS=$(xcrun notarytool info "$SUBMISSION_ID" \
    --apple-id "$APPLE_ID" \
    --password "$APPLE_PASSWORD" \
    --team-id "$APPLE_TEAM_ID" \
    --output-format json | grep -o '"status":"[^"]*"' | cut -d'"' -f4)

set -e

# Check if notarization was accepted
if [ "$NOTARY_STATUS" == "Accepted" ]; then
    echo "✅ Notarization successful! (Status: $NOTARY_STATUS)"
    
    # Get detailed log
    xcrun notarytool log "$SUBMISSION_ID" \
        --apple-id "$APPLE_ID" \
        --password "$APPLE_PASSWORD" \
        --team-id "$APPLE_TEAM_ID" \
        "$BUILD_DIR/notarization_log.json"
        
    echo "   Detailed log saved to: $BUILD_DIR/notarization_log.json"
else
    echo "❌ Notarization failed with status: $NOTARY_STATUS"
    
    # Get detailed log
    xcrun notarytool log "$SUBMISSION_ID" \
        --apple-id "$APPLE_ID" \
        --password "$APPLE_PASSWORD" \
        --team-id "$APPLE_TEAM_ID" \
        "$BUILD_DIR/notarization_log.json"
        
    echo "   Detailed error log saved to: $BUILD_DIR/notarization_log.json"
    exit 1
fi

# --- 5. Staple the notarization ticket to the app ---
echo "📎 [Step 5/5] Stapling the notarization ticket to the app..."
# Small pause to ensure Apple's servers have propagated the notarization
sleep 10
xcrun stapler staple "$APP_PATH"

# Verify stapling
xcrun stapler validate "$APP_PATH"
echo "✅ Stapling complete and verified."

# --- Generate DMG after notarization if needed ---
if [ "$IS_INTEL" = true ]; then
    echo "💿 Skipping Intel DMG creation as it was already created in build-intel-mac.sh"
    # For Intel builds, DMG file was already created, so don't create it again here
    DMG_PATH=$(find "app/build/compose/binaries/intel/dmg" -name "*-intel.dmg" | head -1)
    if [ -n "$DMG_PATH" ]; then
        echo "✅ Intel-compatible DMG location: $DMG_PATH"
    else
        echo "⚠️ Intel DMG file not found."
    fi
else
    echo "💿 Creating DMG after successful notarization..."
    ./gradlew packageDmg
    echo "✅ DMG created at: app/build/compose/binaries/main/dmg/"
fi

# --- Cleanup ---
echo "🧹 Cleaning up temporary zip file..."
rm "$ZIP_PATH"

echo ""
echo "🎉 Notarization process finished successfully!"
if [ "$IS_INTEL" = true ]; then
    echo "    Intel-compatible DMG file can be found at: app/build/compose/binaries/intel/dmg/"
else
    echo "    ARM64-compatible DMG file can be found at: app/build/compose/binaries/main/dmg/"
fi
echo "    To verify app signing, run the following command: spctl -a -vv '$APP_PATH'" 