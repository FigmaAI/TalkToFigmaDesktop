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
APP_NAME="TalkToFigma Desktop"
# The .app bundle is located in the 'app' directory, not 'dmg'
APP_PATH="app/build/compose/binaries/main/app/${APP_NAME}.app"
BUILD_DIR="app/build/manual_notarization"
ZIP_PATH="${BUILD_DIR}/${APP_NAME}.zip"

# Check if required environment variables are set
if [ -z "$APPLE_ID" ] || [ -z "$APPLE_PASSWORD" ] || [ -z "$APPLE_TEAM_ID" ]; then
  echo "❌ Error: Required environment variables (APPLE_ID, APPLE_PASSWORD, APPLE_TEAM_ID) are not set."
  exit 1
fi

# --- 1. Build and sign the app ---
echo "🔨 [Step 1/4] Building the app using './gradlew packageDmg'..."
# We run createDistributable which creates the .app, then packageDmg for the final dmg
./gradlew createDistributable packageDmg

if [ ! -d "$APP_PATH" ]; then
    echo "❌ Error: App not found at path: $APP_PATH. The build might have failed or the path changed."
    exit 1
fi
echo "✅ App built and signed successfully at: $APP_PATH"

# --- 2. Create a zip file for notarization using ditto ---
echo "📦 [Step 2/4] Creating zip file for notarization..."
mkdir -p "$BUILD_DIR"
/usr/bin/ditto -c -k --sequesterRsrc --keepParent "$APP_PATH" "$ZIP_PATH"
echo "✅ Zip file created at: $ZIP_PATH"

# --- 3. Upload and wait for notarization ---
echo "⬆️  [Step 3/4] Submitting app to Apple for notarization with 'notarytool'..."
set +e # Temporarily disable exit-on-error to capture notarytool's output
NOTARY_OUTPUT=$(xcrun notarytool submit "$ZIP_PATH" \
    --apple-id "$APPLE_ID" \
    --password "$APPLE_PASSWORD" \
    --team-id "$APPLE_TEAM_ID" \
    --wait 2>&1)
NOTARY_EXIT_CODE=$?
set -e

# Check if notarization was accepted, even if there are warnings
if echo "$NOTARY_OUTPUT" | grep -q "status: Accepted"; then
    echo "✅ Notarization successful! (Status: Accepted)"
    echo "---------- Apple Notary Log ----------"
    echo "$NOTARY_OUTPUT"
    echo "------------------------------------"
else
    echo "❌ Notarization failed! See log below:"
    echo "$NOTARY_OUTPUT"
    exit 1
fi

# --- 4. Staple the notarization ticket to the app ---
echo "📎 [Step 4/4] Stapling the notarization ticket to the app..."
xcrun stapler staple "$APP_PATH"
echo "✅ Stapling complete."

# --- Cleanup ---
echo "🧹 Cleaning up temporary zip file..."
rm "$ZIP_PATH"

echo ""
echo "🎉 Notarization process finished successfully!"
echo "    You can now find the distributable DMG in: app/build/compose/binaries/main/dmg/"
echo "    To verify the app inside, run: spctl -a -vv '$APP_PATH'" 