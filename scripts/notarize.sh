#!/bin/bash

# =================================================================
# Notarize and Staple the app manually (Final Version)
# This script uses Apple's latest notarization tool and a robust workflow.
# It's fully automated and uses environment variables from direnv.
#
# Usage: ./scripts/notarize.sh
# =================================================================

set -e # Exit immediately if a command exits with a non-zero status.

echo "🚀 Starting notarization process (using notarytool)..."

# --- Configuration ---
APP_NAME="Cursor Talk to Figma desktop"
# The .app bundle is located in the 'app' directory, not 'dmg'
APP_PATH="app/build/compose/binaries/main/app/${APP_NAME}.app"
BUILD_DIR="app/build/manual_notarization"
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

# --- 1. Build and sign the app ---
echo "🔨 [Step 1/5] Building the app using './gradlew createDistributable'..."
# We ONLY run createDistributable which creates the .app, not packageDmg
./gradlew createDistributable

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
echo "💿 Creating DMG after successful notarization..."
./gradlew packageDmg
echo "✅ DMG created at: app/build/compose/binaries/main/dmg/"

# --- Cleanup ---
echo "🧹 Cleaning up temporary zip file..."
rm "$ZIP_PATH"

echo ""
echo "🎉 Notarization process finished successfully!"
echo "    You can now find the distributable DMG in: app/build/compose/binaries/main/dmg/"
echo "    To verify the app inside, run: spctl -a -vv '$APP_PATH'" 